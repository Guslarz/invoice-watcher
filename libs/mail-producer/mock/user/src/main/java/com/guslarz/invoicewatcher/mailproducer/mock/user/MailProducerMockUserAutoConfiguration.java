package com.guslarz.invoicewatcher.mailproducer.mock.user;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MailProducerMockUserInitializer.class)
@EnableConfigurationProperties(MailProducerMockUserProperties.class)
@ConditionalOnBooleanProperty(
    prefix = "invoice-watcher.mail-producer.mock.user",
    name = "enabled"
)
public class MailProducerMockUserAutoConfiguration {
}
