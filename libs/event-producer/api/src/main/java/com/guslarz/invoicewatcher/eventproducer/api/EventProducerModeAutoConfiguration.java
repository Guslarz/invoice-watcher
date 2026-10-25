package com.guslarz.invoicewatcher.eventproducer.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Binds {@link EventProducerProperties} for every implementation of the area, so a missing or unknown mode fails the
 * startup and names the property.
 */
@AutoConfiguration
@EnableConfigurationProperties(EventProducerProperties.class)
public class EventProducerModeAutoConfiguration {
}
