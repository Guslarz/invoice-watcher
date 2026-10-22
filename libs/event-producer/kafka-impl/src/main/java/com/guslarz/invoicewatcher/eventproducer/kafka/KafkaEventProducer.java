package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
public class KafkaEventProducer implements EventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  public void send(String topic, Event event) {
    try {
      kafkaTemplate.send(new ProducerRecord<>(
          topic,
          null,
          event.key(),
          event.value()
      )).get();
    } catch (ExecutionException e) {
      throw new EventDeliveryException("Event not delivered to topic " + topic, e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventDeliveryException("Interrupted while delivering event to topic " + topic, e);
    }
  }
}
