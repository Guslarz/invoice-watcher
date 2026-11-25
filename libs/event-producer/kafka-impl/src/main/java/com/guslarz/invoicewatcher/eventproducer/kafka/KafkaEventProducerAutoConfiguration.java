package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.EventProducerProperties;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@AutoConfiguration
@Import(KafkaEventProducer.class)
@EnableConfigurationProperties(KafkaEventProducerProperties.class)
@ConditionalOnProperty(
    prefix = EventProducerProperties.Constants.PREFIX,
    name = EventProducerProperties.Constants.MODE,
    havingValue = "kafka")
public class KafkaEventProducerAutoConfiguration {

  @Bean
  ProducerFactory<String, Object> producerFactory(KafkaEventProducerProperties properties) {
    return new DefaultKafkaProducerFactory<>(kafkaProducerConfig(properties));
  }

  @Bean
  KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  private Map<String, Object> kafkaProducerConfig(KafkaEventProducerProperties properties) {
    return Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        properties.bootstrapServers(),
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        JacksonJsonSerializer.class,
        JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS,
        false,
        ProducerConfig.ACKS_CONFIG,
        "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
        true,
        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
        15000,
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
        10000,
        ProducerConfig.MAX_BLOCK_MS_CONFIG,
        15000);
  }
}
