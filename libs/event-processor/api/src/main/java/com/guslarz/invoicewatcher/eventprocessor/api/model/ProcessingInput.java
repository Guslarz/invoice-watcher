package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.time.Instant;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

/** Validated event and execution bounds supplied to a processing stage handler. */
@Builder(toBuilder = true)
public record ProcessingInput(
    String tenant,
    String processingId,
    SourceRecordReference source,
    JsonNode payload,
    Instant startedAt,
    Instant deadline) {
  @Override
  public String toString() {
    return "ProcessingInput[redacted]";
  }
}
