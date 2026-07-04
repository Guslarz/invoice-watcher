package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("invoice-watcher.mail-producer.scheduler")
public record MailProducerSchedulerProperties(
    @DefaultValue(Defaults.Rate) Duration rate
) {

  @UtilityClass
  static class Defaults {
    static final String Rate = "10s";
  }
}
