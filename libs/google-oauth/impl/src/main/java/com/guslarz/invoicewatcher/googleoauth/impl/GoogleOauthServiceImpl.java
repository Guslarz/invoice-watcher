package com.guslarz.invoicewatcher.googleoauth.impl;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.GmailGrantDao;
import com.guslarz.invoicewatcher.googleoauth.api.GmailReconnectionRequiredException;
import com.guslarz.invoicewatcher.googleoauth.api.GoogleOauthProperties;
import com.guslarz.invoicewatcher.googleoauth.api.GoogleOauthService;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GoogleOauthServiceImpl implements GoogleOauthService {

  private final GoogleOauthProperties properties;
  private final GmailGrantDao grants;
  private final SecretFactory secrets;
  private final HttpClient client;

  @Autowired
  public GoogleOauthServiceImpl(
      GoogleOauthProperties properties, GmailGrantDao grants, SecretFactory secrets) {
    this(
        properties,
        grants,
        secrets,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  GoogleOauthServiceImpl(
      GoogleOauthProperties properties,
      GmailGrantDao grants,
      SecretFactory secrets,
      HttpClient client) {
    this.properties = properties;
    this.grants = grants;
    this.secrets = secrets;
    this.client = client;
  }

  @Override
  public Secret accessToken(GmailOauthCredentials snapshot) {
    GmailOauthCredentials current =
        grants
            .findGrant(snapshot.username(), snapshot.grantId())
            .orElseThrow(GmailReconnectionRequiredException::new);
    if (current.reconnectRequired()) {
      throw new GmailReconnectionRequiredException();
    }
    var response =
        post(
            "https://oauth2.googleapis.com/token",
            Map.of(
                "client_id",
                properties.clientId(),
                "client_secret",
                properties.clientSecret().getDecrypted(),
                "grant_type",
                "refresh_token",
                "refresh_token",
                current.refreshToken().getDecrypted()));
    tools.jackson.databind.JsonNode json;
    try {
      json = new JsonMapper().readTree(response.body());
    } catch (RuntimeException malformed) {
      throw new IllegalStateException("Google token service returned an invalid response");
    }
    if (response.statusCode() == 400 && "invalid_grant".equals(json.path("error").asString())) {
      grants.updateGrant(
          current.username(),
          current.grantId(),
          current.revision(),
          current.toBuilder().reconnectRequired(true).build());
      throw new GmailReconnectionRequiredException();
    }
    if (response.statusCode() != 200 || !json.hasNonNull("access_token")) {
      throw new IllegalStateException("Google token service temporarily unavailable");
    }
    if (json.hasNonNull("refresh_token")) {
      grants.updateGrant(
          current.username(),
          current.grantId(),
          current.revision(),
          current.toBuilder()
              .refreshToken(secrets.fromPlaintext(json.path("refresh_token").asString()))
              .build());
    }
    return secrets.fromPlaintext(json.path("access_token").asString());
  }

  @Override
  public boolean revoke(Secret refreshToken) {
    try {
      return post(
                  "https://oauth2.googleapis.com/revoke",
                  Map.of("token", refreshToken.getDecrypted()))
              .statusCode()
          == 200;
    } catch (RuntimeException failure) {
      return false;
    }
  }

  private HttpResponse<String> post(String endpoint, Map<String, String> fields) {
    String body =
        fields.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));
    var request =
        HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Google request interrupted");
    } catch (IOException failure) {
      throw new IllegalStateException("Google token service temporarily unavailable");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
