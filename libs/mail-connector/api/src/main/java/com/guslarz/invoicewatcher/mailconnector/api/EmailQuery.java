package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record EmailQuery(
    String folder,
    ZonedDateTime dateFrom
) {
}
