package com.guslarz.invoicewatcher.mailproducer.mock.user;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MailProducerMockUserInitializer.class)
@EnableConfigurationProperties(MailProducerMockUserProperties.class)
public class MailProducerMockUserAutoConfiguration {
}
