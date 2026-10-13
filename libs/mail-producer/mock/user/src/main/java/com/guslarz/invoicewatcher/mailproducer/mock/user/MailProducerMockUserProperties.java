package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.secret.api.Secret;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Mock user created or replaced at startup, for development and the dev environment.
 *
 * @param enabled Whether to create the mock user at startup.
 * @param host IMAP server host of the mock user's mailbox.
 * @param port IMAP server port (IMAPS).
 * @param email E-mail address; used as the user name and the mailbox username.
 * @param password Mailbox password, encrypted with `secret-helper encrypt`.
 * @param folders Folders to scan, e.g. `INBOX`, `Invoices`.
 */
@ConfigurationProperties("invoice-watcher.mail-producer.mock.user")
public record MailProducerMockUserProperties(
    Boolean enabled,
    String host,
    @DefaultValue(Defaults.Port) int port,
    String email,
    Secret password,
    List<String> folders
) {

  @UtilityClass
  class Defaults {
    static final String Port = "993";
  }
}
