package com.guslarz.invoicewatcher.mailproducer.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invoice-watcher.mail-producer.task")
public record MailProducerTaskProperties(
    String topic
) {
}
