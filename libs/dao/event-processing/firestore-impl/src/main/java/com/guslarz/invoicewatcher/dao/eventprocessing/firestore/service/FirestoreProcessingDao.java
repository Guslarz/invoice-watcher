package com.guslarz.invoicewatcher.dao.eventprocessing.firestore.service;

import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.Transaction;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingEntry;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingOutput;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingStatus;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ReplayAudit;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.exception.ProcessingInfrastructureException;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmission;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmissionDecision;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Transactional implementation. No Google type escapes through the ProcessingDao boundary. */
@Component
@RequiredArgsConstructor
public final class FirestoreProcessingDao implements ProcessingDao {
  @Qualifier("processingDatastore")
  private final Datastore datastore;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Override
  public int maxStoredOutcomeBytes() {
    return 900_000;
  }

  @Override
  public ProcessingAdmission acquire(
      String id, String stage, String version, Instant now, Instant leaseUntil, int maxAttempts) {
    return mutate(
        id,
        current -> {
          if (current != null) {
            if (current.status() == ProcessingStatus.COMPLETE) {
              return current;
            }
            if (current.status() == ProcessingStatus.QUARANTINED
                || current.status() == ProcessingStatus.QUARANTINE_PENDING) {
              return current;
            }
            if (current.status() == ProcessingStatus.RUNNING && current.leaseUntil().isAfter(now)) {
              return current;
            }
            if (current.status() == ProcessingStatus.RETRY && current.dueAt().isAfter(now)) {
              return current;
            }
            if (current.attempts() >= maxAttempts) {
              return current;
            }
          }
          return ProcessingEntry.builder()
              .recordVersion(1)
              .id(id)
              .stage(stage)
              .processingVersion(version)
              .status(ProcessingStatus.RUNNING)
              .generation(current == null ? 1 : current.generation() + 1)
              .attempts(current == null ? 1 : current.attempts() + 1)
              .leaseUntil(leaseUntil)
              .dueAt(now)
              .startedAt(current == null ? now : current.startedAt())
              .completedAt(null)
              .failureId(null)
              .reason(null)
              .outputs(List.of())
              .replayRequestId(current == null ? null : current.replayRequestId())
              .failureLocation(null)
              .build();
        },
        (previous, next) -> {
          ProcessingAdmissionDecision decision;
          if (next.status() == ProcessingStatus.COMPLETE) {
            decision = ProcessingAdmissionDecision.COMPLETE;
          } else if (next.status() == ProcessingStatus.QUARANTINED
              || next.status() == ProcessingStatus.QUARANTINE_PENDING
              || (next.attempts() >= maxAttempts && !next.leaseUntil().isAfter(now))) {
            decision = ProcessingAdmissionDecision.QUARANTINE;
          } else if (previous == null || next.generation() != previous.generation()) {
            decision = ProcessingAdmissionDecision.ADMITTED;
          } else {
            decision = ProcessingAdmissionDecision.WAIT;
          }
          return new ProcessingAdmission(decision, next);
        });
  }

  @Override
  public boolean retry(ProcessingEntry claim, Instant now, Instant due, String reason) {
    return changeClaim(claim, now, ProcessingStatus.RETRY, due, null, reason, List.of());
  }

  @Override
  public boolean defer(ProcessingEntry claim, Instant now, Instant due) {
    return mutate(
        claim.id(),
        current -> {
          if (!owns(current, claim, now)) {
            return current;
          }
          return current.toBuilder()
              .recordVersion(1)
              .status(ProcessingStatus.RETRY)
              .attempts(Math.max(0, current.attempts() - 1))
              .leaseUntil(now)
              .dueAt(due)
              .completedAt(null)
              .failureId(null)
              .reason("INFRASTRUCTURE_UNAVAILABLE")
              .outputs(List.of())
              .failureLocation(null)
              .build();
        },
        (previous, next) -> previous != next);
  }

  @Override
  public boolean complete(ProcessingEntry claim, Instant now, List<ProcessingOutput> outputs) {
    return changeClaim(claim, now, ProcessingStatus.COMPLETE, now, null, null, outputs);
  }

  private boolean changeClaim(
      ProcessingEntry claim,
      Instant now,
      ProcessingStatus status,
      Instant due,
      String failure,
      String reason,
      List<ProcessingOutput> outputs) {
    return mutate(
        claim.id(),
        current -> {
          if (!owns(current, claim, now)) {
            return current;
          }
          return current.toBuilder()
              .recordVersion(1)
              .status(status)
              .leaseUntil(now)
              .dueAt(due)
              .completedAt(status == ProcessingStatus.COMPLETE ? now : null)
              .failureId(failure)
              .reason(reason)
              .outputs(outputs)
              .failureLocation(null)
              .build();
        },
        (previous, next) -> previous != next);
  }

  @Override
  public ProcessingEntry quarantine(
      String id,
      String stage,
      String version,
      ProcessingEntry claim,
      Instant now,
      String failureId,
      String reason,
      String failureLocation) {
    return mutate(
        id,
        current -> {
          if (current != null
              && (claim == null
                  || current.generation() != claim.generation()
                  || current.status() == ProcessingStatus.COMPLETE
                  || current.status() == ProcessingStatus.QUARANTINED
                  || current.status() == ProcessingStatus.QUARANTINE_PENDING)) {
            return current;
          }
          return ProcessingEntry.builder()
              .recordVersion(1)
              .id(id)
              .stage(stage)
              .processingVersion(version)
              .status(ProcessingStatus.QUARANTINE_PENDING)
              .generation(current == null ? 1 : current.generation())
              .attempts(current == null ? 0 : current.attempts())
              .leaseUntil(now)
              .dueAt(now)
              .startedAt(current == null ? now : current.startedAt())
              .completedAt(null)
              .failureId(failureId)
              .reason(reason)
              .outputs(List.of())
              .replayRequestId(current == null ? null : current.replayRequestId())
              .failureLocation(failureLocation)
              .build();
        },
        (previous, next) -> next);
  }

  @Override
  public boolean confirmQuarantine(ProcessingEntry entry, Instant now) {
    return mutate(
        entry.id(),
        current -> {
          if (current == null
              || current.status() != ProcessingStatus.QUARANTINE_PENDING
              || current.generation() != entry.generation()) {
            return current;
          }
          return current.toBuilder()
              .recordVersion(1)
              .status(ProcessingStatus.QUARANTINED)
              .leaseUntil(now)
              .dueAt(now)
              .completedAt(null)
              .outputs(List.of())
              .build();
        },
        (previous, next) -> next != null && next.status() == ProcessingStatus.QUARANTINED);
  }

  @Override
  public Optional<ProcessingEntry> find(String id) {
    try {
      return Optional.ofNullable(read(datastore.get(key("Entry", id)), ProcessingEntry.class));
    } catch (RuntimeException failure) {
      throw new ProcessingInfrastructureException();
    }
  }

  @Override
  public List<ProcessingEntry> entries(String stage) {
    try {
      var query =
          Query.newEntityQueryBuilder()
              .setKind("Entry")
              .setFilter(PropertyFilter.eq("stage", stage))
              .build();
      var results = datastore.run(query);
      List<ProcessingEntry> entries = new ArrayList<>();
      while (results.hasNext()) {
        entries.add(read(results.next(), ProcessingEntry.class));
      }
      return List.copyOf(entries);
    } catch (RuntimeException failure) {
      throw new ProcessingInfrastructureException();
    }
  }

  @Override
  public List<ProcessingEntry> pending(String stage, int limit) {
    try {
      var query =
          Query.newEntityQueryBuilder()
              .setKind("Entry")
              .setFilter(
                  CompositeFilter.and(
                      PropertyFilter.eq("stage", stage), PropertyFilter.eq("pending", true)))
              .setLimit(limit)
              .build();
      var results = datastore.run(query);
      List<ProcessingEntry> pending = new ArrayList<>();
      while (results.hasNext()) {
        pending.add(read(results.next(), ProcessingEntry.class));
      }
      return List.copyOf(pending);
    } catch (RuntimeException failure) {
      throw new ProcessingInfrastructureException();
    }
  }

  @Override
  public void published(String id, String outputId) {
    mutate(
        id,
        current -> {
          if (current == null) {
            return null;
          }
          var outputs =
              current.outputs().stream()
                  .map(output -> output.id().equals(outputId) ? output.confirmed() : output)
                  .toList();
          return current.toBuilder()
              .recordVersion(1)
              .outputs(outputs)
              .failureLocation(null)
              .build();
        },
        (previous, next) -> true);
  }

  @Override
  public ReplayAudit authorize(ReplayAudit request) {
    return transaction(
        tx -> {
          var current = read(tx.get(key("Replay", request.id())), ReplayAudit.class);
          if (current != null) {
            if (!sameSelection(current, request)) {
              throw new IllegalArgumentException("Replay selection conflict");
            }
            return current;
          }
          tx.put(entity("Replay", request.id(), request.stage(), request));
          return request;
        });
  }

  @Override
  public Optional<ReplayAudit> replay(String id) {
    try {
      return Optional.ofNullable(read(datastore.get(key("Replay", id)), ReplayAudit.class));
    } catch (RuntimeException failure) {
      throw new ProcessingInfrastructureException();
    }
  }

  @Override
  public void replayProgress(String id, long nextOffset, boolean finished, String failure) {
    transaction(
        tx -> {
          var current = read(tx.get(key("Replay", id)), ReplayAudit.class);
          if (current == null) {
            throw new IllegalArgumentException("Replay request missing");
          }
          var next =
              current.toBuilder()
                  .recordVersion(1)
                  .id(id)
                  .nextOffset(Math.max(nextOffset, current.nextOffset()))
                  .finished(current.finished() || finished)
                  .failure(current.failure() == null ? failure : current.failure())
                  .build();
          tx.put(entity("Replay", id, current.stage(), next));
          return true;
        });
  }

  @Override
  public boolean reopen(String id, String requestId, Instant now) {
    return transaction(
        tx -> {
          var audit = read(tx.get(key("Replay", requestId)), ReplayAudit.class);
          var current = read(tx.get(key("Entry", id)), ProcessingEntry.class);
          if (audit == null) {
            return false;
          }
          if (current == null) {
            var next =
                ProcessingEntry.builder()
                    .recordVersion(1)
                    .id(id)
                    .stage(audit.stage())
                    .processingVersion(audit.processingVersion())
                    .status(ProcessingStatus.RETRY)
                    .generation(1)
                    .attempts(0)
                    .leaseUntil(now)
                    .dueAt(now)
                    .startedAt(now)
                    .completedAt(null)
                    .failureId(null)
                    .reason(null)
                    .outputs(List.of())
                    .replayRequestId(requestId)
                    .failureLocation(null)
                    .build();
            tx.put(entity("Entry", id, next.stage(), next));
            return true;
          }
          if (!current.stage().equals(audit.stage())
              || !current.processingVersion().equals(audit.processingVersion())) {
            return false;
          }
          if (current.status() == ProcessingStatus.COMPLETE
              || requestId.equals(current.replayRequestId())) {
            return false;
          }
          if (current.status() != ProcessingStatus.QUARANTINED) {
            return false;
          }
          var next =
              ProcessingEntry.builder()
                  .recordVersion(1)
                  .id(id)
                  .stage(current.stage())
                  .processingVersion(current.processingVersion())
                  .status(ProcessingStatus.RETRY)
                  .generation(current.generation() + 1)
                  .attempts(0)
                  .leaseUntil(now)
                  .dueAt(now)
                  .startedAt(now)
                  .completedAt(null)
                  .failureId(null)
                  .reason(null)
                  .outputs(List.of())
                  .replayRequestId(requestId)
                  .failureLocation(null)
                  .build();
          tx.put(entity("Entry", id, next.stage(), next));
          return true;
        });
  }

  private boolean sameSelection(ReplayAudit first, ReplayAudit second) {
    return first.stage().equals(second.stage())
        && first.processingVersion().equals(second.processingVersion())
        && first.topic().equals(second.topic())
        && first.partition() == second.partition()
        && first.fromOffset() == second.fromOffset()
        && first.toOffset() == second.toOffset()
        && first.limit() == second.limit()
        && Double.compare(first.rate(), second.rate()) == 0
        && first.operator().equals(second.operator())
        && first.reason().equals(second.reason());
  }

  private boolean owns(ProcessingEntry current, ProcessingEntry claim, Instant now) {
    return current != null
        && current.status() == ProcessingStatus.RUNNING
        && current.generation() == claim.generation()
        && current.leaseUntil().isAfter(now);
  }

  private <T> T mutate(
      String id, Function<ProcessingEntry, ProcessingEntry> update, Result<T> result) {
    return transaction(
        tx -> {
          ProcessingEntry previous = read(tx.get(key("Entry", id)), ProcessingEntry.class);
          ProcessingEntry next = update.apply(previous);
          if (next != null && next != previous) {
            tx.put(entity("Entry", id, next.stage(), next));
          }
          return result.apply(previous, next);
        });
  }

  private <T> T transaction(Function<Transaction, T> action) {
    for (int attempt = 0; attempt < 5; attempt++) {
      Transaction tx;
      try {
        tx = datastore.newTransaction();
      } catch (RuntimeException failure) {
        throw new ProcessingInfrastructureException();
      }
      try {
        T result = action.apply(tx);
        tx.commit();
        return result;
      } catch (IllegalArgumentException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        if (attempt == 4) {
          throw new ProcessingInfrastructureException();
        }
      } finally {
        if (tx.isActive()) {
          try {
            tx.rollback();
          } catch (RuntimeException failure) {
            throw new ProcessingInfrastructureException();
          }
        }
      }
    }
    throw new ProcessingInfrastructureException();
  }

  private Key key(String kind, String id) {
    return datastore.newKeyFactory().setKind(kind).newKey(id);
  }

  private Entity entity(String kind, String id, String stage, Object value) {
    byte[] bytes = mapper.writeValueAsBytes(value);
    if (bytes.length > 900_000) {
      throw new IllegalArgumentException("Processing state exceeds capacity");
    }
    return Entity.newBuilder(key(kind, id))
        .set(
            "pending",
            value instanceof ProcessingEntry entry
                && entry.outputs().stream().anyMatch(o -> !o.published()))
        .set("recordVersion", 1)
        .set("stage", stage)
        .set("data", BlobValue.newBuilder(Blob.copyFrom(bytes)).setExcludeFromIndexes(true).build())
        .build();
  }

  private <T> T read(Entity entity, Class<T> type) {
    if (entity == null) {
      return null;
    }
    if (entity.getLong("recordVersion") != 1) {
      throw new ProcessingInfrastructureException();
    }
    return mapper.readValue(entity.getBlob("data").toByteArray(), type);
  }

  @FunctionalInterface
  private interface Result<T> {
    T apply(ProcessingEntry previous, ProcessingEntry next);
  }
}
