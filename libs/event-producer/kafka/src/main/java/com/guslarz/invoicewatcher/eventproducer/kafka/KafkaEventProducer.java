package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class KafkaEventProducer<Key, Value> implements EventProducer<Key, Value> {

  private final KafkaTemplate<Key, Value> kafkaTemplate;

  @Override
  public void send(String topic, Event<Key, Value> event) {
    kafkaTemplate.send(new ProducerRecord<>(
        topic,
        null,
        event.key(),
        event.value()
    ));
  }
}
