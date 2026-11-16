package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventEnvelope;
import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

    // when
    Throwable thrown = catchThrowable(() -> uut.send("all-mails", event()));

    // then
    assertThat(thrown).isNull();
  }

  @Test
  @DisplayName("EVENT-012: throws EventDeliveryException when the broker does not confirm the event")
  void throwsWhenNotConfirmed() {
    // given
    TimeoutException cause = new TimeoutException("delivery timed out");
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(cause));

    // when
    Throwable thrown = catchThrowable(() -> uut.send("all-mails", event()));

    // then
    assertThat(thrown)
        .isInstanceOf(EventDeliveryException.class)
        .hasCause(cause);
  }

  @Test
  @DisplayName("EVENT-003, EVENT-007: sends the unchanged tenant key and complete envelope")
  void sendsEnvelopeWithTenantKey() {
    // given
    Event<String> event = event();
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    // when
    uut.send("all-mails", event);

    // then
    ArgumentCaptor<ProducerRecord<String, Object>> record = ArgumentCaptor.captor();
    verify(kafkaTemplate).send(record.capture());
    assertThat(record.getValue().topic()).isEqualTo("all-mails");
    assertThat(record.getValue().key()).isEqualTo("jan@example.com");
    assertThat(record.getValue().value()).isEqualTo(event.value());
  }

  private static Event<String> event() {
    return Event.<String>builder()
        .key("jan@example.com")
        .value(new EventEnvelope<>("example.created", 1, "value"))
        .build();
  }
}
