package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record MailQuery(
    String folder,
    ZonedDateTime dateFrom
) {
}
