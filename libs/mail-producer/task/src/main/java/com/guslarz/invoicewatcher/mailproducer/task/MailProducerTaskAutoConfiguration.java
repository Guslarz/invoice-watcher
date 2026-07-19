package com.guslarz.invoicewatcher.mailproducer.task;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(MailProducerTaskProperties.class)
@Import({MailProducerTaskImpl.class, MailProcessorService.class})
public class MailProducerTaskAutoConfiguration {
}
