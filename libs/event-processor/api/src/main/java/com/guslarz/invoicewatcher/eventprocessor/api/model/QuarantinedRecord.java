package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record QuarantinedRecord(
    String failureId,
    String stage,
    String processingVersion,
    int attempts,
    Instant failedAt,
    String reason,
    String tenant,
    String sourceId,
    String processingId,
    RawEventRecord original) {
  @Override
  public String toString() {
    return "QuarantinedRecord[redacted]";
  }
}
