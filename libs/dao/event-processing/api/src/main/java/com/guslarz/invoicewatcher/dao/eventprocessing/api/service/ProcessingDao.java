package com.guslarz.invoicewatcher.dao.eventprocessing.api.service;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingEntry;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingOutput;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ReplayAudit;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Atomic operations; implementations own transaction boundaries and persistence types. */
public interface ProcessingDao {
  int maxStoredOutcomeBytes();

  ProcessingAdmission acquire(
      String id, String stage, String version, Instant now, Instant leaseUntil, int maxAttempts);

  boolean retry(ProcessingEntry claim, Instant now, Instant due, String reason);

  boolean defer(ProcessingEntry claim, Instant now, Instant due);

  boolean complete(ProcessingEntry claim, Instant now, List<ProcessingOutput> outputs);

  ProcessingEntry quarantine(
      String id,
      String stage,
      String version,
      ProcessingEntry claim,
      Instant now,
      String failureId,
      String reason,
      String failureLocation);

  boolean confirmQuarantine(ProcessingEntry entry, Instant now);

  Optional<ProcessingEntry> find(String id);

  List<ProcessingEntry> pending(String stage, int limit);

  void published(String id, String outputId);

  ReplayAudit authorize(ReplayAudit request);

  Optional<ReplayAudit> replay(String id);

  void replayProgress(String id, long nextOffset, boolean finished, String failure);

  boolean reopen(String id, String requestId, Instant now);

  List<ProcessingEntry> entries(String stage);
}
