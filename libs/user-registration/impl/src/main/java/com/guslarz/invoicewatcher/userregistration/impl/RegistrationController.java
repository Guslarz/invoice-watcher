package com.guslarz.invoicewatcher.userregistration.impl;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
public class RegistrationController {

  private final RegistrationService service;

  @GetMapping("/api/session")
  public Map<String, Object> session(@AuthenticationPrincipal OidcUser identity) {
    return identity == null
        ? Map.of("authenticated", false)
        : Map.of("authenticated", true, "email", service.email(identity));
  }

  @GetMapping("/api/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/api/v1/me")
  public Map<String, Object> me(@AuthenticationPrincipal OidcUser identity) {
    return view(
        service
            .find(identity)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @PostMapping("/api/v1/registration")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> register(
      @AuthenticationPrincipal OidcUser identity,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    only(body, "folders");
    var pending =
        (RegistrationLoginHandler.PendingGrant) request.getSession().getAttribute("gmail-grant");
    if (pending == null
        || pending.expiresAt() < System.currentTimeMillis()
        || !pending.subject().equals(service.owner(identity))) {
      throw new IllegalArgumentException("Connect Gmail before registering");
    }
    if (pending.version() != -1) {
      throw new IllegalArgumentException("Registration already exists");
    }
    var user = service.register(identity, pending.credentials(), folders(body));
    request.getSession().removeAttribute("gmail-grant");
    return view(user);
  }

  @PutMapping("/api/v1/me/configuration")
  public Map<String, Object> edit(
      @AuthenticationPrincipal OidcUser identity,
      @RequestBody JsonNode body,
      HttpServletRequest request) {
    only(body, "version", "folders");
    var session = Objects.requireNonNull(request.getSession());
    var pending = (RegistrationLoginHandler.PendingGrant) session.getAttribute("gmail-grant");
    if (pending != null) {
      if (pending.expiresAt() < System.currentTimeMillis()
          || pending.version() != version(body)
          || !pending.subject().equals(service.owner(identity))) {
        throw new IllegalArgumentException("Connect Gmail again before saving");
      }
      var user =
          service.connectConfigured(identity, version(body), pending.credentials(), folders(body));
      session.removeAttribute("gmail-grant");
      return view(user);
    }
    return view(service.edit(identity, version(body), folders(body)));
  }

  @PostMapping("/api/v1/me/gmail/connect")
  public Map<String, String> connect(
      @AuthenticationPrincipal OidcUser identity, HttpServletRequest request) {
    var current = service.find(identity);
    request
        .getSession()
        .setAttribute(
            "gmail-flow",
            RegistrationLoginHandler.Flow.create(
                identity, current.map(User::configurationVersion).orElse(-1L)));
    return Map.of("url", "/oauth2/authorization/gmail");
  }

  @PostMapping("/api/v1/me/gmail/disconnect")
  public Map<String, Object> disconnect(
      @AuthenticationPrincipal OidcUser identity, @RequestBody JsonNode body) {
    only(body, "version");
    return Map.of("revoked", service.disconnect(identity, version(body), false));
  }

  @DeleteMapping("/api/v1/registration")
  public Map<String, Object> delete(
      @AuthenticationPrincipal OidcUser identity, @RequestBody JsonNode body) {
    only(body, "version");
    return Map.of("revoked", service.disconnect(identity, version(body), true));
  }

  static Map<String, Object> view(User user) {
    List<Map<String, Object>> folders =
        user.scanConfigs().stream()
            .flatMap(c -> c.folders().stream())
            .map(
                f ->
                    Map.<String, Object>of(
                        "id",
                        f.id(),
                        "name",
                        f.name(),
                        "startDate",
                        f.startDate() == null ? "" : f.startDate().toString()))
            .toList();
    boolean reconnect =
        user.scanConfigs().stream()
            .anyMatch(c -> ((GmailOauthCredentials) c.credentials()).reconnectRequired());
    return Map.of(
        "email",
        user.name(),
        "version",
        user.configurationVersion(),
        "folders",
        folders,
        "connected",
        !user.scanConfigs().isEmpty(),
        "reconnectionRequired",
        reconnect);
  }

  private static long version(JsonNode body) {
    if (!body.has("version")
        || !body.path("version").isIntegralNumber()
        || body.path("version").asLong() < 0) {
      throw new IllegalArgumentException("Configuration version required");
    }
    return body.path("version").asLong();
  }

  private static List<RegistrationService.FolderInput> folders(JsonNode body) {
    if (!body.path("folders").isArray() || body.path("folders").size() > 16) {
      throw new IllegalArgumentException("Choose between 1 and 16 folders");
    }
    var result = new ArrayList<RegistrationService.FolderInput>();
    for (var folder : body.path("folders")) {
      only(folder, "id", "name", "startDate");
      result.add(
          new RegistrationService.FolderInput(
              text(folder, "id"), text(folder, "name"), text(folder, "startDate")));
    }
    return result;
  }

  private static String text(JsonNode node, String field) {
    if (!node.hasNonNull(field)) {
      return null;
    }
    if (!node.path(field).isString()) {
      throw new IllegalArgumentException("Text value required");
    }
    return node.path(field).asString();
  }

  private static void only(JsonNode body, String... fields) {
    if (!body.isObject()
        || body.propertyNames().stream().anyMatch(f -> !List.of(fields).contains(f))) {
      throw new IllegalArgumentException("Unsupported configuration field");
    }
  }
}
