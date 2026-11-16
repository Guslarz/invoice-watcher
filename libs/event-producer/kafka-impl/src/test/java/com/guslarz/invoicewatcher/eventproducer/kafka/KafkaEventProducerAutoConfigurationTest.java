package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.EventEnvelope;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
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

  @Test
  @DisplayName("EVENT-007: configured serializer writes the envelope without Java type headers")
  void omitsJavaTypeHeaders() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        "invoice-watcher.event-producer.mode=kafka",
        "invoice-watcher.event-producer.kafka.bootstrap-servers=localhost:9092");

    // when
    runner.run(context -> {
      var factory = context.getBean(DefaultKafkaProducerFactory.class);
      RecordHeaders headers = new RecordHeaders();
      try (JacksonJsonSerializer<EventEnvelope<String>> serializer = new JacksonJsonSerializer<>()) {
        serializer.configure(factory.getConfigurationProperties(), false);
        byte[] bytes = serializer.serialize("example", headers,
            new EventEnvelope<>("example.created", 1, "value"));

        // then
        assertThat(headers.toArray()).isEmpty();
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
            .contains("\"eventType\":\"example.created\"", "\"payload\":\"value\"");
      }
    });
  }

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
