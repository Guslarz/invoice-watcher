package com.guslarz.invoicewatcher.mailconnector.impl;

import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * IMAP connection limits.
 *
 * @param connectTimeout Optional. How long connecting to the mail server may take, at least one
 *     second, e.g. `30s`.
 * @param readTimeout Optional. How long to wait for each response of the mail server, at least one
 *     second, e.g. `60s`.
 */
@Validated
@ConfigurationProperties("invoice-watcher.mail-connector.imap")
public record MailConnectorImapProperties(
    @NotNull @DurationMin(seconds = 1) @DefaultValue(Defaults.ConnectTimeout) Duration connectTimeout,
    @NotNull @DurationMin(seconds = 1) @DefaultValue(Defaults.ReadTimeout) Duration readTimeout
) {

  @UtilityClass
  static class Defaults {
    static final String ConnectTimeout = "30s";
    static final String ReadTimeout = "60s";
  }
}
