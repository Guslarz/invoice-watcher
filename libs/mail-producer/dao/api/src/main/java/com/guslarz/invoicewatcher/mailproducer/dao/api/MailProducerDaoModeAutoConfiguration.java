package com.guslarz.invoicewatcher.mailproducer.dao.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Binds {@link MailProducerDaoProperties} for every implementation of the area, so a missing or unknown mode fails the
 * startup and names the property.
 */
@AutoConfiguration
@EnableConfigurationProperties(MailProducerDaoProperties.class)
public class MailProducerDaoModeAutoConfiguration {
}
