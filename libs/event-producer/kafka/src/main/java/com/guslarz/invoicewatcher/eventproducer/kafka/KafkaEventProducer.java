package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class KafkaEventProducer<KeyT, ValueT> implements EventProducer<KeyT, ValueT> {

  private final KafkaTemplate<KeyT, ValueT> kafkaTemplate;

  @Override
  public void send(String topic, Event<KeyT, ValueT> event) {
    kafkaTemplate.send(new ProducerRecord<>(
        topic,
        null,
        event.key(),
        event.value()
    ));
  }
}
