package com.guslarz.invoicewatcher.mailconnector.impl;

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

class MailConnectorImapPropertiesTest {

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
  @DisplayName("MAIL-013: fails to start with a read timeout below one second")
  void failsWithReadTimeoutBelowOneSecond() {
    // given
    ApplicationContextRunner runner = uut
        .withPropertyValues("invoice-watcher.mail-connector.imap.read-timeout=0s");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of("invoice-watcher.mail-connector.imap.read-timeout"));
    });
  }

  @Test
  @DisplayName("MAIL-013: fails to start with a connect timeout below one second")
  void failsWithConnectTimeoutBelowOneSecond() {
    // given
    ApplicationContextRunner runner = uut
        .withPropertyValues("invoice-watcher.mail-connector.imap.connect-timeout=500ms");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of("invoice-watcher.mail-connector.imap.connect-timeout"));
    });
  }

  @Test
  @DisplayName("MAIL-013: connects within 30 seconds and waits 60 seconds for a response by default")
  void defaultsToThirtyAndSixtySeconds() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      MailConnectorImapProperties properties = context.getBean(MailConnectorImapProperties.class);
      assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
      assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(60));
    });
  }

  @EnableConfigurationProperties(MailConnectorImapProperties.class)
  static class PropertiesConfiguration {
  }
}
