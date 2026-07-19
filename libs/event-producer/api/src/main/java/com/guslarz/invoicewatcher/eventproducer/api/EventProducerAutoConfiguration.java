package com.guslarz.invoicewatcher.eventproducer.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(EventProducerProperties.class)
public class EventProducerAutoConfiguration {
}
