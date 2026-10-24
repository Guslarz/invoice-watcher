package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.EventProducerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka event backend, active when `invoice-watcher.event-producer.mode` is `kafka`.
 *
 * @param bootstrapServers Kafka brokers, e.g. `kafka:9092`.
 */
@ConfigurationProperties(prefix = EventProducerProperties.Constants.Prefix + ".kafka")
public record KafkaEventProducerProperties(
    String bootstrapServers
) {
}
