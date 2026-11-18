package com.guslarz.invoicewatcher.dao.eventprocessing.api.entity;

import java.time.Instant;
import lombok.Builder;

/** Bounded replay selection and publication cursor; no input payload is stored here. */
@Builder(toBuilder = true)
public record ReplayAudit(
    int recordVersion,
    String id,
    String stage,
    String processingVersion,
    String topic,
    int partition,
    long fromOffset,
    long toOffset,
    int limit,
    double rate,
    String operator,
    String reason,
    Instant createdAt,
    long nextOffset,
    boolean finished,
    String failure) {
  @Override
  public String toString() {
    return "ReplayAudit[redacted]";
  }
}
