package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

@Builder(toBuilder = true)
public record ProcessingResultPayload(
    String resultId,
    String processingId,
    List<SourceRecordReference> sourceRefs,
    String method,
    String processingVersion,
    Instant startedAt,
    Instant completedAt,
    ProcessingOutcome outcome,
    List<String> reasonCodes,
    JsonNode evidence) {
  public ProcessingResultPayload {
    sourceRefs = List.copyOf(sourceRefs);
    reasonCodes = List.copyOf(reasonCodes);
  }

  @Override
  public String toString() {
    return "ProcessingResultPayload[redacted]";
  }
}
