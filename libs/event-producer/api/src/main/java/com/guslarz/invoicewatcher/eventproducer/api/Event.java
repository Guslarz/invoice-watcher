package com.guslarz.invoicewatcher.eventproducer.api;

import lombok.Builder;

@Builder
public record Event<T>(
    String key,
    EventEnvelope<T> value
) {
}
