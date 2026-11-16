package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailCredentialsDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailScanConfigDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailServerDocument;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FirestoreMapperTest {

  private final SecretFactory secrets = mock(SecretFactory.class);
  private final FirestoreMapper uut = new FirestoreMapper(secrets);

  @Test
  @DisplayName("maps a user without scan configs to a user with none")
  void mapsUserWithoutScanConfigs() {
    // given
    UserDocument doc = UserDocument.builder().id("jan@example.com").name("jan@example.com").build();

    // when
    User user = uut.toUser(doc, List.of());

    // then
    assertThat(user.scanConfigs()).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "basic")
  @DisplayName("USER-002: legacy and typed password documents map through the visitor")
  void mapsPasswordCredentials(String type) {
    // given
    var password = mock(Secret.class);
    when(secrets.fromEncrypted("encrypted-password")).thenReturn(password);
    var doc = MailCredentialsDocument.builder()
        .type(type).username("user").password("encrypted-password").build();
    // when
    var user = uut.toUser(userDocument(doc), List.of());
    // then
    assertThat(user.scanConfigs().getFirst().credentials())
        .isInstanceOfSatisfying(BasicAuthMailCredentials.class, credentials -> {
          assertThat(credentials.username()).isEqualTo("user");
          assertThat(credentials.password()).isSameAs(password);
        });
  }

  @Test
  @DisplayName("USER-002: OAuth documents retain grant state in both visitor conversions")
  void roundTripsOauthCredentials() {
    // given
    var refresh = mock(Secret.class);
    when(refresh.getEncrypted()).thenReturn("encrypted-refresh");
    when(secrets.fromEncrypted("encrypted-refresh")).thenReturn(refresh);
    var doc = MailCredentialsDocument.builder().type("gmail-oauth").username("user")
        .grantId("grant").refreshToken("encrypted-refresh").grantRevision(7L)
        .reconnectRequired(true).build();
    // when
    var user = uut.toUser(userDocument(doc), List.of());
    var result = uut.toUserDocument(user).getScanConfigs().getFirst().getCredentials();
    // then
    assertThat(result).isEqualTo(doc);
    assertThat(user.scanConfigs().getFirst().credentials())
        .isInstanceOfSatisfying(GmailOauthCredentials.class, credentials -> {
          assertThat(credentials.revision()).isEqualTo(7);
          assertThat(credentials.reconnectRequired()).isTrue();
          assertThat(credentials.refreshToken()).isSameAs(refresh);
        });
  }

  @Test
  @DisplayName("USER-002: missing optional OAuth state defaults to the initial grant state")
  void defaultsOptionalOauthState() {
    // given
    var refresh = mock(Secret.class);
    when(secrets.fromEncrypted("encrypted-refresh")).thenReturn(refresh);
    var doc = MailCredentialsDocument.builder().type("gmail-oauth").username("user")
        .grantId("grant").refreshToken("encrypted-refresh").build();
    // when
    var user = uut.toUser(userDocument(doc), List.of());
    // then
    assertThat(user.scanConfigs().getFirst().credentials())
        .isInstanceOfSatisfying(GmailOauthCredentials.class, credentials -> {
          assertThat(credentials.revision()).isZero();
          assertThat(credentials.reconnectRequired()).isFalse();
        });
  }

  @Test
  @DisplayName("USER-002: unknown credential types cannot fall back to password authentication")
  void rejectsUnknownCredentials() {
    // given
    var doc = MailCredentialsDocument.builder().type("unknown").build();
    // when
    var failure = catchThrowable(() -> uut.toUser(userDocument(doc), List.of()));
    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(secrets);
  }

  private UserDocument userDocument(MailCredentialsDocument credentials) {
    return UserDocument.builder().id("user").name("user")
        .scanConfigs(List.of(MailScanConfigDocument.builder()
            .server(MailServerDocument.builder().host("imap.example.com").port(993).build())
            .credentials(credentials).folderIds(List.of()).build())).build();
  }
}
