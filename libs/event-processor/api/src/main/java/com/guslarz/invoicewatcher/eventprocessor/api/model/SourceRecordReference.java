package com.guslarz.invoicewatcher.eventprocessor.api.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record SourceRecordReference(
    String sourceId,
    String eventType,
    int schemaVersion,
    String topic,
    int partition,
    long offset) {}
