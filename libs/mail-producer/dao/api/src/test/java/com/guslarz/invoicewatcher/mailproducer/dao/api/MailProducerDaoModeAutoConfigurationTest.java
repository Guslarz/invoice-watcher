package com.guslarz.invoicewatcher.mailproducer.dao.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MailProducerDaoModeAutoConfigurationTest {

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MailProducerDaoModeAutoConfiguration.class));

  private static List<ConfigurationPropertyName> invalidProperties(Throwable startupFailure) {
    Throwable cause = startupFailure;
    while (cause != null && !(cause instanceof BindException)) {
      cause = cause.getCause();
    }
    BindException bindException = (BindException) Objects.requireNonNull(cause);
    if (!(bindException.getCause() instanceof BindValidationException validation)) {
      return List.of(bindException.getName());
    }
    String prefix = validation.getValidationErrors().getName().toString();
    return validation.getValidationErrors().getAllErrors().stream()
        .map(error -> ConfigurationPropertyName.adapt(
            prefix + "." + ((FieldError) error).getField(), '.'))
        .toList();
  }

  @Test
  @DisplayName("USER-010: fails to start without mode")
  void failsWithoutMode() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure()))
          .containsExactly(ConfigurationPropertyName.of("invoice-watcher.mail-producer.dao.mode"));
    });
  }

  @Test
  @DisplayName("USER-010: fails to start with an unknown mode")
  void failsWithUnknownMode() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues("invoice-watcher.mail-producer.dao.mode=jdbc");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure()))
          .containsExactly(ConfigurationPropertyName.of("invoice-watcher.mail-producer.dao.mode"));
    });
  }
}
