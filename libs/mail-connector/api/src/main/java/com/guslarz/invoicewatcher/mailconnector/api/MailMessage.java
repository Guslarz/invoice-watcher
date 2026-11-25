package com.guslarz.invoicewatcher.mailconnector.api;

import com.guslarz.invoicewatcher.mailattachment.api.service.AttachmentPart;
import java.time.ZonedDateTime;
import lombok.Builder;

@Builder
public record MailMessage(
    String sender,
    String title,
    ZonedDateTime date,
    String content,
    String contentType,
    AttachmentPart attachmentRoot) {
  public MailMessage(
      String sender, String title, ZonedDateTime date, String content, String contentType) {
    this(sender, title, date, content, contentType, null);
  }
}
