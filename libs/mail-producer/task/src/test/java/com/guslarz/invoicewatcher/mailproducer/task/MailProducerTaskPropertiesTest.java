package com.guslarz.invoicewatcher.mailproducer.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MailProducerTaskPropertiesTest {

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
  @DisplayName("EVENT-013: fails to start without the topic")
  void failsWithoutTopic() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of("invoice-watcher.mail-producer.task.topic"));
    });
  }

  @Test
  @DisplayName("EVENT-002: binds the topic")
  void bindsTopic() {
    // given
    ApplicationContextRunner runner = uut
        .withPropertyValues("invoice-watcher.mail-producer.task.topic=all-mails");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      assertThat(context.getBean(MailProducerTaskProperties.class).topic()).isEqualTo("all-mails");
    });
  }

  @EnableConfigurationProperties(MailProducerTaskProperties.class)
  static class PropertiesConfiguration {
  }
}
