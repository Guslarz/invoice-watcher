package com.guslarz.invoicewatcher.mailproducer.task;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Publishing of scanned messages.
 *
 * @param topic Required. Topic every scanned message is published to, e.g. `all-mails`.
 */
@Validated
@ConfigurationProperties(prefix = "invoice-watcher.mail-producer.task")
public record MailProducerTaskProperties(
    @NotBlank String topic
) {
}
