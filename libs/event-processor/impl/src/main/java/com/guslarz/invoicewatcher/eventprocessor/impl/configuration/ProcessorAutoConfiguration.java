package com.guslarz.invoicewatcher.eventprocessor.impl.configuration;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ProcessorProperties;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ReplayProperties;
import com.guslarz.invoicewatcher.eventprocessor.impl.service.DefaultProcessorRuntime;
import com.guslarz.invoicewatcher.eventprocessor.impl.service.DefaultReplayService;
import com.guslarz.invoicewatcher.eventprocessor.impl.service.ProcessorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(
    afterName = {
      "com.guslarz.invoicewatcher.dao.eventprocessing.firestore.configuration.ProcessingFirestoreAutoConfiguration",
      "com.guslarz.invoicewatcher.eventprocessor.kafka.configuration.ProcessorKafkaAutoConfiguration"
    })
@ConditionalOnBean(ProcessingStage.class)
@EnableConfigurationProperties({ProcessorProperties.class, ReplayProperties.class})
@Import({ProcessorMetrics.class, DefaultProcessorRuntime.class, DefaultReplayService.class})
public class ProcessorAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  MeterRegistry processorMeterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean("processorClock")
  Clock processorClock() {
    return Clock.systemUTC();
  }

  @Bean(name = "processorExecutor", destroyMethod = "shutdownNow")
  ExecutorService processorExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
