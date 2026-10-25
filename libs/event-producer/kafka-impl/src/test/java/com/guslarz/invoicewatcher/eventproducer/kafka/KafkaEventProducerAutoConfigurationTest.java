package com.guslarz.invoicewatcher.eventproducer.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventProducerAutoConfigurationTest {

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(KafkaEventProducerAutoConfiguration.class));

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
  @DisplayName("EVENT-013: fails to start in kafka mode without bootstrap servers")
  void failsWithoutBootstrapServers() {
    // given
    ApplicationContextRunner runner = uut
        .withPropertyValues("invoice-watcher.event-producer.mode=kafka");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of("invoice-watcher.event-producer.kafka.bootstrap-servers"));
    });
  }

  @Test
  @DisplayName("EVENT-010: starts in kafka mode with bootstrap servers")
  void startsWithBootstrapServers() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        "invoice-watcher.event-producer.mode=kafka",
        "invoice-watcher.event-producer.kafka.bootstrap-servers=localhost:9092"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(KafkaEventProducer.class);
    });
  }
}
