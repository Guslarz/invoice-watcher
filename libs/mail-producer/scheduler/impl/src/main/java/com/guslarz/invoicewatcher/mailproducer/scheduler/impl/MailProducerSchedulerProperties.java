package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Scan schedule.
 *
 * @param rate Optional. Time between the starts of consecutive scans, at least one second, e.g.
 *     `30s`, `5m`, `1h` or ISO-8601 `PT1H`.
 */
@Validated
@ConfigurationProperties("invoice-watcher.mail-producer.scheduler")
public record MailProducerSchedulerProperties(
    @NotNull @DurationMin(seconds = 1) @DefaultValue(Defaults.Rate) Duration rate
) {

  @UtilityClass
  static class Defaults {
    static final String Rate = "1h";
  }
}
