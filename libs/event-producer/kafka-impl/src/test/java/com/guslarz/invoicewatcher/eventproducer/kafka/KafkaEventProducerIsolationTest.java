package com.guslarz.invoicewatcher.eventproducer.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducerProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.ProducerFactory;

class KafkaEventProducerIsolationTest {
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner().withUserConfiguration(TestApplication.class);

  @Test
  @DisplayName(
      "EVENT-010: disabled Kafka producer and its dependencies create no producer infrastructure")
  void remainsInactiveWhenNotSelected() {
    // given
    // when
    for (var runner :
        List.of(
            uut,
            uut.withPropertyValues("invoice-watcher.event-producer.mode="),
            uut.withPropertyValues("invoice-watcher.event-producer.mode=future"),
            uut.withPropertyValues("invoice-watcher.event-producer.mode=unknown"))) {
      runner.run(
          context -> {
            // then
            assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(EventProducer.class)
                .doesNotHaveBean(EventProducerProperties.class)
                .doesNotHaveBean(KafkaEventProducerProperties.class)
                .doesNotHaveBean(ProducerFactory.class)
                .doesNotHaveBean(KafkaOperations.class);
          });
    }
  }

  @Test
  @DisplayName("EVENT-010: discovered Kafka producer starts independently when selected")
  void startsWithOwnGraph() {
    // given
    var runner =
        uut.withPropertyValues(
            "invoice-watcher.event-producer.mode=kafka",
            "invoice-watcher.event-producer.kafka.bootstrap-servers=localhost:9092");
    // when
    runner.run(
        context -> {
          // then
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(EventProducer.class)
              .hasSingleBean(ProducerFactory.class)
              .hasSingleBean(KafkaOperations.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
