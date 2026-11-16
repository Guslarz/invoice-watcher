package com.guslarz.invoicewatcher.mailproducer.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MailSourceIdentityTest {

  @Test
  @DisplayName("EVENT-005: canonical identity has a fixed UTF-8 vector and normalizes host and INBOX")
  void usesCanonicalEncoding() {
    // given
    MailProcessorContext context = context("tenant", "IMAP.EXAMPLE.COM", 993, "żółć:a", "inbox");

    // when
    String uut = MailSourceIdentity.create(context, 7, 13);

    // then
    assertThat(uut).isEqualTo("imap-v1:221891437de98f62a2799c1c94c62d3783d9d6e64ba8b916be59e28cd8ac2046");
    assertThat(uut).isEqualTo(MailSourceIdentity.create(
        context("tenant", "imap.example.com", 993, "żółć:a", "INBOX"), 7, 13));
  }

  @Test
  @DisplayName("EVENT-005: configuration recreation, progress and credential changes preserve identity")
  void ignoresConfigurationAndCredentialState() {
    // given
    MailProcessorContext original = context("tenant", "imap.example.com", 993, "login", "INBOX");
    Secret secret = mock(Secret.class);
    FolderConfig folder = new FolderConfig("new-folder-id", "INBOX", null, 7L, 99L);
    MailScanConfig oauth = original.scanConfig().toBuilder()
        .credentials(new GmailOauthCredentials("login", "new-grant", secret, 42, false))
        .folders(List.of(folder)).build();
    MailProcessorContext recreated = new MailProcessorContext(original.user(), oauth, folder);

    // when
    String uut = MailSourceIdentity.create(original, 7, 13);

    // then
    assertThat(uut).isEqualTo(MailSourceIdentity.create(recreated, 7, 13));
    verifyNoInteractions(secret);
  }

  @Test
  @DisplayName("EVENT-005: length-prefixed components distinguish ambiguous concatenations")
  void distinguishesComponentBoundaries() {
    // given
    MailProcessorContext first = context("a:b", "c", 993, "login", "INBOX");
    MailProcessorContext second = context("a", "b:c", 993, "login", "INBOX");

    // when
    String uut = MailSourceIdentity.create(first, 7, 13);

    // then
    assertThat(uut).isNotEqualTo(MailSourceIdentity.create(second, 7, 13));
  }

  @Test
  @DisplayName("EVENT-006: every occurrence component distinguishes otherwise identical mail")
  void distinguishesOccurrences() {
    // given
    MailProcessorContext original = context("tenant", "imap.example.com", 993, "login", "Bills");
    List<MailProcessorContext> changed = List.of(
        context("other", "imap.example.com", 993, "login", "Bills"),
        context("tenant", "other.example.com", 993, "login", "Bills"),
        context("tenant", "imap.example.com", 994, "login", "Bills"),
        context("tenant", "imap.example.com", 993, "other", "Bills"),
        context("tenant", "imap.example.com", 993, "login", "bills"));

    // when
    String uut = MailSourceIdentity.create(original, 7, 13);

    // then
    assertThat(changed).allSatisfy(context ->
        assertThat(MailSourceIdentity.create(context, 7, 13)).isNotEqualTo(uut));
    assertThat(MailSourceIdentity.create(original, 8, 13)).isNotEqualTo(uut);
    assertThat(MailSourceIdentity.create(original, 7, 14)).isNotEqualTo(uut);
  }

  @Test
  @DisplayName("EVENT-008: unsigned 32-bit maximum UID values are valid")
  void acceptsMaximumImapIds() {
    // given
    MailProcessorContext context = context("tenant", "host", 993, "login", "INBOX");

    // when
    String uut = MailSourceIdentity.create(context, 4294967295L, 4294967295L);

    // then
    assertThat(uut).matches("imap-v1:[0-9a-f]{64}");
  }

  @ParameterizedTest
  @MethodSource("invalidContexts")
  @DisplayName("EVENT-008: absent or invalid identity context is rejected without revealing its values")
  void rejectsInvalidContext(MailProcessorContext context) {
    // given
    // when
    Throwable uut = catchThrowable(() -> MailSourceIdentity.create(context, 7, 13));

    // then
    assertThat(uut).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing or invalid mail source identity component");
  }

  static Stream<MailProcessorContext> invalidContexts() {
    return Stream.of(
        new MailProcessorContext(null, null, null),
        context(null, "host", 993, "login", "INBOX"),
        context(" ", "host", 993, "login", "INBOX"),
        context("tenant", null, 993, "login", "INBOX"),
        context("tenant", " ", 993, "login", "INBOX"),
        context("tenant", "host", 0, "login", "INBOX"),
        context("tenant", "host", 65536, "login", "INBOX"),
        context("tenant", "host", 993, null, "INBOX"),
        context("tenant", "host", 993, " ", "INBOX"),
        context("tenant", "host", 993, "login", null),
        context("tenant", "host", 993, "login", " "));
  }

  static MailProcessorContext context(String tenant, String host, int port, String account,
      String folderName) {
    FolderConfig folder = new FolderConfig("folder-id", folderName, null, null, null);
    MailScanConfig mailbox = new MailScanConfig(new MailServer(host, port),
        new BasicAuthMailCredentials(account, null), List.of(folder));
    return new MailProcessorContext(User.builder().name(tenant).scanConfigs(List.of(mailbox)).build(), mailbox, folder);
  }
}
