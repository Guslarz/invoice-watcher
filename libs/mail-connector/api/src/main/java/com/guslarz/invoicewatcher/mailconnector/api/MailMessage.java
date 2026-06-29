package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record MailMessage(
    String sender,
    String title,
    ZonedDateTime date,
    String content
) {
}
