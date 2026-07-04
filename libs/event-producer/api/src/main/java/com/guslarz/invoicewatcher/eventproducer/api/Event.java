package com.guslarz.invoicewatcher.eventproducer.api;

public record Event<KeyT, ValueT>(
    KeyT key,
    ValueT value
) {
}
