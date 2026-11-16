package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BaseMailCredentialsVisitor;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/** Canonical, tenant-scoped IMAP occurrence identity. Never includes secrets. */
@UtilityClass
class MailSourceIdentity {

  private static final long MAX_IMAP_ID = 4294967295L;

  static String create(MailProcessorContext context, long uidValidity, long uid) {
    requireImapId(uidValidity);
    requireImapId(uid);
    if (context.user() == null || context.scanConfig() == null
        || context.scanConfig().server() == null || context.scanConfig().credentials() == null
        || context.folderConfig() == null) {
      throw invalidIdentity();
    }
    var server = context.scanConfig().server();
    if (server.port() < 1 || server.port() > 65535) {
      throw invalidIdentity();
    }
    String account = context.scanConfig().credentials().accept(new AccountNameVisitor());
    String folder = required(context.folderConfig().name());
    String[] components = {
        "imap-v1", required(context.user().name()),
        required(server.host()).toLowerCase(Locale.ROOT), Integer.toString(server.port()),
        required(account), "INBOX".equalsIgnoreCase(folder) ? "INBOX" : folder,
        Long.toString(uidValidity), Long.toString(uid)
    };
    MessageDigest digest = sha256();
    for (String component : components) {
      byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
      digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
      digest.update((byte) ':');
      digest.update(bytes);
    }
    return "imap-v1:" + HexFormat.of().formatHex(digest.digest());
  }

  static void requireImapId(long value) {
    if (value < 1 || value > MAX_IMAP_ID) {
      throw invalidIdentity();
    }
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) {
      throw invalidIdentity();
    }
    return value;
  }

  private static IllegalArgumentException invalidIdentity() {
    return new IllegalArgumentException("Missing or invalid mail source identity component");
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static class AccountNameVisitor extends BaseMailCredentialsVisitor<String> {

    @Override
    public String visitBasicMailCredentials(BasicAuthMailCredentials credentials) {
      return credentials.username();
    }

    @Override
    public String visitGmailOauthCredentials(GmailOauthCredentials credentials) {
      return credentials.username();
    }
  }
}
