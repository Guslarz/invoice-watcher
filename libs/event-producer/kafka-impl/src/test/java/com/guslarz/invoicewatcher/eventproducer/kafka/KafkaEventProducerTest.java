package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaEventProducerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
  private final KafkaEventProducer uut = new KafkaEventProducer(kafkaTemplate);

  @Test
  @DisplayName("EVENT-011: returns once the broker confirms the event")
  void returnsWhenConfirmed() {
    // given
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    // when / then
    assertThatCode(() -> uut.send("all-mails", event())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("EVENT-012: throws EventDeliveryException when the broker does not confirm the event")
  void throwsWhenNotConfirmed() {
    // given
    TimeoutException cause = new TimeoutException("delivery timed out");
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(cause));

    // when / then
    assertThatThrownBy(() -> uut.send("all-mails", event()))
        .isInstanceOf(EventDeliveryException.class)
        .hasCause(cause);
  }

  private static Event event() {
    return Event.builder()
        .key("jan@example.com")
        .value("value")
        .build();
  }
}
