package com.guslarz.invoicewatcher.eventproducer.api;

import lombok.Builder;

@Builder
public record Event(
    String key,
    Object value
) {
}
