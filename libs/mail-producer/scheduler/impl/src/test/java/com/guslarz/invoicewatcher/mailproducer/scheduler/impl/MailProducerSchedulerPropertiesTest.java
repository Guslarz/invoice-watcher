package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MailProducerSchedulerPropertiesTest {

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withUserConfiguration(PropertiesConfiguration.class);

  private static List<ConfigurationPropertyName> invalidProperties(Throwable startupFailure) {
    BindValidationException exception =
        (BindValidationException) NestedExceptionUtils.getRootCause(startupFailure);
    String prefix = Objects.requireNonNull(exception).getValidationErrors().getName().toString();
    return exception.getValidationErrors().getAllErrors().stream()
        .map(error -> ConfigurationPropertyName.adapt(
            prefix + "." + ((FieldError) error).getField(), '.'))
        .toList();
  }

  @Test
  @DisplayName("MAIL-001: fails to start with a rate below one second")
  void failsWithRateBelowOneSecond() {
    // given
    ApplicationContextRunner runner = uut
        .withPropertyValues("invoice-watcher.mail-producer.scheduler.rate=0s");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of("invoice-watcher.mail-producer.scheduler.rate"));
    });
  }

  @Test
  @DisplayName("MAIL-001: scans every hour by default")
  void defaultsToOneHour() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(MailProducerSchedulerProperties.class).rate())
          .isEqualTo(Duration.ofHours(1));
    });
  }

  @EnableConfigurationProperties(MailProducerSchedulerProperties.class)
  static class PropertiesConfiguration {
  }
}
