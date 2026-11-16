package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import java.time.ZonedDateTime;

/** Mail-specific evidence; source identity is independent of the shared envelope. */
public record MailIngestedPayload(
    String sourceId,
    String sender,
    String title,
    ZonedDateTime date,
    String content,
    String contentType) {

  static MailIngestedPayload from(String sourceId, MailMessage message) {
    return new MailIngestedPayload(sourceId, message.sender(), message.title(), message.date(),
        message.content(), message.contentType());
  }
}
