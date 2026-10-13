package com.guslarz.invoicewatcher.mailproducer.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Publishing of scanned messages.
 *
 * @param topic Topic every scanned message is published to, e.g. `all-mails`.
 */
@ConfigurationProperties(prefix = "invoice-watcher.mail-producer.task")
public record MailProducerTaskProperties(
    String topic
) {
}
