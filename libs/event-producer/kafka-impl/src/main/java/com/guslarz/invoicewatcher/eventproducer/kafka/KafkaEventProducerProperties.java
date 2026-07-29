package com.guslarz.invoicewatcher.eventproducer.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invoice-watcher.event-producer.kafka")
public record KafkaEventProducerProperties(
    String bootstrapServers
) {
}
