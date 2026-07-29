package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class KafkaEventProducer implements EventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  public void send(String topic, Event event) {
    kafkaTemplate.send(new ProducerRecord<>(
        topic,
        null,
        event.key(),
        event.value()
    ));
  }
}
