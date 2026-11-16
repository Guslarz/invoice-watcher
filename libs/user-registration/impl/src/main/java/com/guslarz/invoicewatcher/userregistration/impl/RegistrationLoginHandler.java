package com.guslarz.invoicewatcher.userregistration.impl;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.ConfigurationConflictException;
import com.guslarz.invoicewatcher.googleoauth.api.GmailReconnectionRequiredException;
import com.guslarz.invoicewatcher.googleoauth.api.GoogleOauthService;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RegistrationLoginHandler
    implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

  private final RegistrationService registration;
  private final GoogleOauthService google;
  private final SecretFactory secrets;
  private final OAuth2AuthorizedClientService clients;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    var token = (OAuth2AuthenticationToken) authentication;
    var identity = (OidcUser) token.getPrincipal();
    String id = token.getAuthorizedClientRegistrationId();
    var session = Objects.requireNonNull(request.getSession());
    var pendingFlow = (Flow) session.getAttribute("gmail-flow");
    try {
      registration.email(identity);
      if ("gmail".equals(id)) {
        var flow = (Flow) session.getAttribute("gmail-flow");
        session.removeAttribute("gmail-flow");
        if (flow == null
            || flow.expiresAt() < System.currentTimeMillis()
            || !registration.owner(flow.identity()).equals(registration.owner(identity))
            || !Objects.equals(flow.identity().getEmail(), identity.getEmail())) {
          log.warn("Gmail callback rejected: missing, expired or mismatched connection flow");
          restore(request, response, flow);
          response.sendRedirect("/?connection=failed");
          return;
        }
        var authorized = clients.loadAuthorizedClient(id, token.getName());
        if (authorized == null
            || !authorized.getAccessToken().getScopes().contains("https://mail.google.com/")) {
          throw new IllegalArgumentException("Gmail permission missing");
        }
        var current = registration.find(identity);
        GmailOauthCredentials previous =
            current.stream()
                .flatMap(u -> u.scanConfigs().stream())
                .map(c -> (GmailOauthCredentials) c.credentials())
                .findFirst()
                .orElse(null);
        var refreshToken = authorized.getRefreshToken();
        var refresh =
            refreshToken == null ? null : secrets.fromPlaintext(refreshToken.getTokenValue());
        if (refresh == null && previous != null && !previous.reconnectRequired()) {
          refresh = previous.refreshToken();
        }
        if (refresh == null) {
          throw new IllegalArgumentException("Offline access missing");
        }
        var grant =
            new GmailOauthCredentials(
                identity.getEmail(), UUID.randomUUID().toString(), refresh, 0, false);
        if (flow.version() < 0 || current.isPresent() && current.get().scanConfigs().isEmpty()) {
          if (flow.version() < 0 && current.isPresent()
              || flow.version() >= 0
                  && (current.isEmpty()
                      || current.get().configurationVersion() != flow.version())) {
            throw new ConfigurationConflictException();
          }
          request
              .getSession()
              .setAttribute(
                  "gmail-grant",
                  new PendingGrant(
                      registration.owner(identity),
                      grant,
                      flow.version(),
                      System.currentTimeMillis() + 600000));
        } else {
          registration.connect(identity, flow.version(), grant, refreshToken != null);
        }
        response.sendRedirect("/?connection=connected");
      } else {
        var current = registration.find(identity);
        var grant =
            current.stream()
                .flatMap(u -> u.scanConfigs().stream())
                .map(c -> (GmailOauthCredentials) c.credentials())
                .findFirst();
        if (grant.isPresent()) {
          try {
            google.accessToken(grant.get());
          } catch (GmailReconnectionRequiredException expired) {
            request
                .getSession()
                .setAttribute(
                    "gmail-flow", Flow.create(identity, current.get().configurationVersion()));
            response.sendRedirect("/oauth2/authorization/gmail");
            return;
          } catch (IllegalStateException temporary) {
            logFailure("grant-refresh", temporary);
            response.sendRedirect("/?connection=temporary-error");
            return;
          }
        }
        response.sendRedirect("/");
      }
    } catch (RuntimeException failure) {
      logFailure("registration-callback", failure);
      if ("gmail".equals(id)) {
        restore(request, response, pendingFlow);
      } else {
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
      }
      response.sendRedirect("/?connection=failed");
    } finally {
      clients.removeAuthorizedClient(id, token.getName());
    }
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    logFailure("authentication-callback", exception);
    var session = request.getSession(false);
    var flow = session == null ? null : (Flow) session.getAttribute("gmail-flow");
    if (session != null) {
      session.removeAttribute("gmail-flow");
    }
    restore(request, response, flow);
    boolean denied =
        exception instanceof OAuth2AuthenticationException oauth
            && "access_denied".equals(oauth.getError().getErrorCode());
    response.sendRedirect(denied ? "/?connection=denied" : "/?connection=failed");
  }

  private void logFailure(String stage, RuntimeException failure) {
    String code = "none";
    if (failure instanceof OAuth2AuthenticationException oauth) {
      // Provider descriptions/messages may contain credentials or callback data. Log only known codes.
      code = switch (oauth.getError().getErrorCode()) {
        case "access_denied", "invalid_client", "invalid_grant", "invalid_request",
            "invalid_scope", "invalid_token", "invalid_token_response", "invalid_user_info_response",
            "authorization_request_not_found", "invalid_state_parameter", "invalid_nonce",
            "invalid_id_token", "server_error", "temporarily_unavailable" ->
            oauth.getError().getErrorCode();
        default -> "other";
      };
    }
    log.warn(
        "Google OAuth failure: stage={}, type={}, code={}, cause-type={}",
        stage,
        failure.getClass().getSimpleName(),
        code,
        failure.getCause() == null ? "none" : failure.getCause().getClass().getSimpleName());
  }

  private void restore(HttpServletRequest request, HttpServletResponse response, Flow flow) {
    var context = SecurityContextHolder.createEmptyContext();
    if (flow != null) {
      context.setAuthentication(
          new OAuth2AuthenticationToken(
              flow.identity(), flow.identity().getAuthorities(), "google"));
    }
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
  }

  public record Flow(OidcUser identity, long version, long expiresAt) {
    static Flow create(OidcUser identity, long version) {
      return new Flow(identity, version, System.currentTimeMillis() + 600000);
    }
  }

  public record PendingGrant(
      String subject, GmailOauthCredentials credentials, long version, long expiresAt) {
    @Override
    public String toString() {
      return "PendingGrant[redacted]";
    }
  }
}
