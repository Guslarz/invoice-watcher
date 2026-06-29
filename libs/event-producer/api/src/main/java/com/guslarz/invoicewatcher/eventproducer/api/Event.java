package com.guslarz.invoicewatcher.eventproducer.api;

public record Event<Key, Value>(
    Key key,
    Value value
) {
}
