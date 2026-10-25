package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.secret.api.Secret;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Mock user created or replaced at startup, for development and the dev environment.
 *
 * @param enabled Optional. Whether to create the mock user at startup.
 * @param host Required when enabled. IMAP server host of the mock user's mailbox.
 * @param port Optional. IMAP server port (IMAPS), between 1 and 65535.
 * @param email Required when enabled. E-mail address; used as the user name and the mailbox
 *     username.
 * @param password Required when enabled. Mailbox password, encrypted with
 *     `secret-helper encrypt`.
 * @param folders Required when enabled, at least one. Folders to scan, e.g. `INBOX`, `Invoices`.
 */
@Validated
@ConfigurationProperties(prefix = MailProducerMockUserProperties.Constants.Prefix)
public record MailProducerMockUserProperties(
    Boolean enabled,
    @NotBlank String host,
    @Min(1) @Max(65535) @DefaultValue(Defaults.Port) int port,
    @NotBlank String email,
    @NotNull Secret password,
    @NotEmpty List<String> folders
) {

  @UtilityClass
  class Constants {
    static final String Prefix = "invoice-watcher.mail-producer.mock.user";
  }

  @UtilityClass
  class Defaults {
    static final String Port = "993";
  }
}
