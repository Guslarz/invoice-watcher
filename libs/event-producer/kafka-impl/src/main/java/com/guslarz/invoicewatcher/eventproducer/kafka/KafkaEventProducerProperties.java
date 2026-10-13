package com.guslarz.invoicewatcher.eventproducer.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka event backend, active when `invoice-watcher.event-producer.mode` is `kafka`.
 *
 * @param bootstrapServers Kafka brokers, e.g. `kafka:9092`.
 */
@ConfigurationProperties(prefix = "invoice-watcher.event-producer.kafka")
public record KafkaEventProducerProperties(
    String bootstrapServers
) {
}
