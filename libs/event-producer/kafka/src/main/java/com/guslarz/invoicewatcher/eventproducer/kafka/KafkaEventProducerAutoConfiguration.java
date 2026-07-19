package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.eventproducer.api.EventProducerProperties;
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

import java.util.Map;

@AutoConfiguration
@Import(KafkaEventProducer.class)
@EnableConfigurationProperties(KafkaEventProducerProperties.class)
@ConditionalOnProperty(
    prefix = EventProducerProperties.Constants.Prefix,
    name = EventProducerProperties.Constants.Mode,
    havingValue = "kafka"
)
public class KafkaEventProducerAutoConfiguration {

  @Bean
  ProducerFactory<String, Object> producerFactory(KafkaEventProducerProperties properties) {
    return new DefaultKafkaProducerFactory<>(
        kafkaProducerConfig(properties)
    );
  }

  @Bean
  KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  private Map<String, Object> kafkaProducerConfig(KafkaEventProducerProperties properties) {
    return Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers(),
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class
    );
  }
}
