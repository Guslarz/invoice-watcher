package com.guslarz.invoicewatcher.mailproducer.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Publishing of scanned messages.
 *
 * @param expectedRetention Optional. Required effective mail-topic retention for evidence replay.
 * @param topic Required. Topic every scanned message is published to, e.g. `all-mails`.
 */
@Validated
@ConfigurationProperties(prefix = "invoice-watcher.mail-producer.task")
public record MailProducerTaskProperties(
    @NotBlank String topic,
    @DefaultValue("7d") @NotNull @DurationMin(seconds = 1) Duration expectedRetention) {
  @ConstructorBinding
  public MailProducerTaskProperties {}

  public MailProducerTaskProperties(String topic) {
    this(topic, Duration.ofDays(7));
  }
}
