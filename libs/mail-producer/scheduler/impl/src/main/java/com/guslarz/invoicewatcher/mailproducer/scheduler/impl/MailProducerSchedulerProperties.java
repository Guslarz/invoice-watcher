package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Scan schedule.
 *
 * @param rate Time between the starts of consecutive scans, e.g. `30s`, `5m`, `1h` or ISO-8601
 *     `PT1H`.
 */
@ConfigurationProperties("invoice-watcher.mail-producer.scheduler")
public record MailProducerSchedulerProperties(
    @DefaultValue(Defaults.Rate) Duration rate
) {

  @UtilityClass
  static class Defaults {
    static final String Rate = "1h";
  }
}
