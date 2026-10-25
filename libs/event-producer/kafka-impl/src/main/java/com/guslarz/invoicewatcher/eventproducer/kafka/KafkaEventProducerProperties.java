package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.EventProducerProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka event backend, active when `invoice-watcher.event-producer.mode` is `kafka`.
 *
 * @param bootstrapServers Required in `kafka` mode. Kafka brokers, e.g. `kafka:9092`.
 */
@Validated
@ConfigurationProperties(prefix = EventProducerProperties.Constants.Prefix + ".kafka")
public record KafkaEventProducerProperties(
    @NotBlank String bootstrapServers
) {
}
