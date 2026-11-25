package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class KafkaEventProducer implements EventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  private final KafkaEventProducerProperties properties;

  @Override
  public void verifyTopic(String topic, long retentionMs) {
    try (Admin admin =
        Admin.create(
            Map.of(
                "bootstrap.servers",
                properties.bootstrapServers(),
                "default.api.timeout.ms",
                10000,
                "request.timeout.ms",
                10000))) {
      var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
      var config =
          admin.describeConfigs(List.of(resource)).all().get(10, TimeUnit.SECONDS).get(resource);
      if (!"delete".equals(config.get("cleanup.policy").value())
          || Long.parseLong(config.get("retention.ms").value()) != retentionMs
          || !"-1".equals(config.get("retention.bytes").value())
          || !"LogAppendTime".equals(config.get("message.timestamp.type").value())) {
        throw new EventDeliveryException("Unsuitable mail topic retention settings", null);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new EventDeliveryException("Topic policy check interrupted", null);
    } catch (java.util.concurrent.ExecutionException
        | TimeoutException
        | KafkaException exception) {
      throw new EventDeliveryException("Topic policy unavailable", null);
    }
  }

  @Override
  public <T> void send(String topic, Event<T> event) {
    try {
      kafkaTemplate
          .send(new ProducerRecord<>(topic, null, event.key(), event.value()))
          .get(15, TimeUnit.SECONDS);
    } catch (TimeoutException exception) {
      throw new EventDeliveryException("Event delivery unconfirmed", null);
    } catch (ExecutionException e) {
      throw new EventDeliveryException("Event not delivered to topic " + topic, e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventDeliveryException("Interrupted while delivering event to topic " + topic, e);
    }
  }
}
