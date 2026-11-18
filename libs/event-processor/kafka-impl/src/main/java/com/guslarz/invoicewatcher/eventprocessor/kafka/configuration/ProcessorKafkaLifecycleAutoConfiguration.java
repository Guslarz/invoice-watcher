package com.guslarz.invoicewatcher.eventprocessor.kafka.configuration;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.kafka.service.ProcessorKafkaLifecycle;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(
    afterName =
        "com.guslarz.invoicewatcher.eventprocessor.impl.configuration.ProcessorAutoConfiguration")
@ConditionalOnBean(ProcessingStage.class)
@Import(ProcessorKafkaLifecycle.class)
public class ProcessorKafkaLifecycleAutoConfiguration {}
