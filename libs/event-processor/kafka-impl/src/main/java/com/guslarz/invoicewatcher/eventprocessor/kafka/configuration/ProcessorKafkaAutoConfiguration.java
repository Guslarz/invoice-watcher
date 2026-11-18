package com.guslarz.invoicewatcher.eventprocessor.kafka.configuration;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.kafka.properties.ProcessorKafkaProperties;
import com.guslarz.invoicewatcher.eventprocessor.kafka.service.KafkaProcessorTransport;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnBean(ProcessingStage.class)
@EnableConfigurationProperties(ProcessorKafkaProperties.class)
public class ProcessorKafkaAutoConfiguration {
  @Bean(destroyMethod = "close")
  KafkaProcessorTransport processorTransport(
      ProcessorKafkaProperties properties, Environment environment) {
    Map<String, Object> security =
        Binder.get(environment)
            .bind("spring.kafka.properties", Bindable.mapOf(String.class, Object.class))
            .orElse(Map.of());
    return new KafkaProcessorTransport(properties, Objects.requireNonNull(security));
  }
}
