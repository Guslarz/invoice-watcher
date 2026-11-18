package com.guslarz.invoicewatcher.dao.eventprocessing.api.entity;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/** Versioned durable lifecycle snapshot, with a monotonically increasing fence. */
@Builder(toBuilder = true)
public record ProcessingEntry(
    int recordVersion,
    String id,
    String stage,
    String processingVersion,
    ProcessingStatus status,
    long generation,
    int attempts,
    Instant leaseUntil,
    Instant dueAt,
    Instant startedAt,
    Instant completedAt,
    String failureId,
    String reason,
    List<ProcessingOutput> outputs,
    String replayRequestId,
    String failureLocation) {

  public ProcessingEntry {
    outputs = List.copyOf(outputs);
  }

  @Override
  public String toString() {
    return "ProcessingEntry[" + status + "]";
  }
}
