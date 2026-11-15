package com.guslarz.invoicewatcher.googleoauth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.GmailGrantDao;
import com.guslarz.invoicewatcher.googleoauth.api.GmailReconnectionRequiredException;
import com.guslarz.invoicewatcher.googleoauth.api.GoogleOauthProperties;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class GoogleOauthServiceImplTest {
  private final GmailGrantDao grants = mock(GmailGrantDao.class);
  private final SecretFactory secrets = mock(SecretFactory.class);
  private final HttpClient client = mock(HttpClient.class);
  private final GoogleOauthServiceImpl uut =
      new GoogleOauthServiceImpl(
          new GoogleOauthProperties("fixture-client", secret("client-secret")),
          grants,
          secrets,
          client);

  @Test
  @DisplayName("MAIL-014: invalid_grant requires reconnection without changing configuration")
  void marksRevokedGrantWithConditionalRevision() throws Exception {
    // given
    var grant = grant();
    when(grants.findGrant(grant.username(), grant.grantId())).thenReturn(Optional.of(grant));
    response(400, "{\"error\":\"invalid_grant\"}");
    // when
    var failure = catchThrowable(() -> uut.accessToken(grant));
    // then
    assertThat(failure).isInstanceOf(GmailReconnectionRequiredException.class);
    verify(grants)
        .updateGrant(
            eq(grant.username()),
            eq(grant.grantId()),
            eq(4L),
            eq(grant.toBuilder().reconnectRequired(true).build()));
  }

  @Test
  @DisplayName("MAIL-014: provider outages do not invalidate an existing grant")
  void preservesGrantOnTemporaryFailure() throws Exception {
    // given
    var grant = grant();
    when(grants.findGrant(grant.username(), grant.grantId())).thenReturn(Optional.of(grant));
    response(503, "{\"error\":\"temporarily_unavailable\"}");
    // when
    var failure = catchThrowable(() -> uut.accessToken(grant));
    // then
    assertThat(failure).isInstanceOf(IllegalStateException.class);
    verify(grants, never()).updateGrant(any(), any(), ArgumentMatchers.anyLong(), any());
  }

  @Test
  @DisplayName(
      "SECRET-004 SECRET-007 MAIL-011: access tokens are transient and refresh tokens remain"
          + " encrypted")
  void exchangesStoredGrantWithoutReturningItsRefreshToken() throws Exception {
    // given
    var grant = grant();
    var access = secret("access-token");
    when(grants.findGrant(grant.username(), grant.grantId())).thenReturn(Optional.of(grant));
    when(secrets.fromPlaintext("access-token")).thenReturn(access);
    response(200, "{\"access_token\":\"access-token\",\"expires_in\":3600}");
    // when
    var result = uut.accessToken(grant);
    // then
    assertThat(result).isSameAs(access);
    verify(grants, never()).updateGrant(any(), any(), ArgumentMatchers.anyLong(), any());
  }

  private GmailOauthCredentials grant() {
    return new GmailOauthCredentials(
        "user@example.com", "grant", secret("refresh-token"), 4, false);
  }

  private Secret secret(String plaintext) {
    var secret = mock(Secret.class);
    when(secret.getDecrypted()).thenReturn(plaintext);
    return secret;
  }

  @SuppressWarnings("unchecked")
  private void response(int status, String body) throws Exception {
    var response = (HttpResponse<String>) mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
  }
}
