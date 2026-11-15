package com.guslarz.invoicewatcher.mailconnector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionProperties;
import com.guslarz.invoicewatcher.mailconnector.api.OauthAccessTokenProvider;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import jakarta.mail.Session;
import jakarta.mail.Store;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MailAuthenticationTest {
  @Test
  @DisplayName("MAIL-011: Gmail uses XOAUTH2 access tokens with password mechanisms disabled")
  @SuppressWarnings("unchecked")
  void authenticatesWithAccessTokenOnly() throws Exception {
    // given
    var provider = (ObjectProvider<OauthAccessTokenProvider>) mock(ObjectProvider.class);
    var tokens = mock(OauthAccessTokenProvider.class);
    var refresh = mock(Secret.class);
    var access = mock(Secret.class);
    var credentials = new GmailOauthCredentials("user@example.com", "g", refresh, 0, false);
    when(provider.getObject()).thenReturn(tokens);
    when(tokens.accessToken(credentials)).thenReturn(access);
    when(access.getDecrypted()).thenReturn("access-token");
    var session = mock(Session.class);
    var store = mock(Store.class);
    when(session.getStore("imaps")).thenReturn(store);
    var settings = new AtomicReference<Properties>();
    var uut =
        new MailConnectionFactoryImpl(
            new MailConnectorImapProperties(Duration.ofSeconds(1), Duration.ofSeconds(1)),
            provider);
    try (var sessions = mockStatic(Session.class)) {
      sessions
          .when(() -> Session.getInstance(any(Properties.class)))
          .thenAnswer(
              call -> {
                settings.set(call.getArgument(0));
                return session;
              });
      // when
      uut.createConnection(new MailConnectionProperties("imap.gmail.com", 993, credentials));
      // then
      verify(store).connect("imap.gmail.com", 993, "user@example.com", "access-token");
      verify(refresh, never()).getDecrypted();
      assertThat(settings.get().getProperty("mail.imaps.auth.mechanisms")).isEqualTo("XOAUTH2");
      assertThat(settings.get().getProperty("mail.imaps.auth.login.disable")).isEqualTo("true");
      assertThat(settings.get().getProperty("mail.imaps.auth.plain.disable")).isEqualTo("true");
      assertThat(settings.get().getProperty("mail.imaps.ssl.checkserveridentity"))
          .isEqualTo("true");
    }
  }

  @Test
  @DisplayName(
      "MAIL-011: manually provisioned passwords remain supported without Google token services")
  void retainsPasswordAuthentication() throws Exception {
    // given
    var password = mock(Secret.class);
    when(password.getDecrypted()).thenReturn("fixture-password");
    var session = mock(Session.class);
    var store = mock(Store.class);
    when(session.getStore("imaps")).thenReturn(store);
    var uut =
        new MailConnectionFactoryImpl(
            new MailConnectorImapProperties(Duration.ofSeconds(1), Duration.ofSeconds(1)), null);
    try (var sessions = mockStatic(Session.class)) {
      sessions.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
      // when
      uut.createConnection(
          new MailConnectionProperties(
              "imap.example.com", 993, new BasicAuthMailCredentials("user", password)));
      // then
      verify(store).connect("imap.example.com", 993, "user", "fixture-password");
    }
  }
}
