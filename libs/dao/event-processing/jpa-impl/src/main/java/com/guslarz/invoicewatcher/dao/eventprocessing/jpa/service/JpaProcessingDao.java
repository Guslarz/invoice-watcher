package com.guslarz.invoicewatcher.dao.eventprocessing.jpa.service;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingEntry;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingOutput;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingStatus;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ReplayAudit;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.exception.ProcessingInfrastructureException;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmission;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmissionDecision;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.dao.eventprocessing.jpa.configuration.ProcessingJpaAutoConfiguration;
import com.guslarz.invoicewatcher.dao.eventprocessing.jpa.entity.ProcessingStateEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** SQL transactions protect accepted state and its embedded immutable outbox together. */
@Component
@RequiredArgsConstructor
public final class JpaProcessingDao implements ProcessingDao {
  @Qualifier(ProcessingJpaAutoConfiguration.Beans.ENTITY_MANAGER_FACTORY)
  private final EntityManagerFactory factory;

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
    return transaction(
        manager -> Optional.ofNullable(read(manager, "Entry", id, ProcessingEntry.class)));
  }

  @Override
  public List<ProcessingEntry> entries(String stage) {
    return transaction(manager ->
        manager
            .createQuery(
                "select s from ProcessingStateEntity s where s.kind = 'Entry' and s.stage = :stage",
                ProcessingStateEntity.class)
            .setParameter("stage", stage)
            .getResultList()
            .stream()
            .map(entity -> decode(entity, ProcessingEntry.class))
            .toList());
  }

  @Override
  public List<ProcessingEntry> pending(String stage, int limit) {
    return transaction(manager ->
        manager
            .createQuery(
                "select s from ProcessingStateEntity s where s.kind = 'Entry' and s.stage = :stage"
                    + " and s.pending = true",
                ProcessingStateEntity.class)
            .setParameter("stage", stage)
            .setMaxResults(limit)
            .getResultList()
            .stream()
            .map(entity -> decode(entity, ProcessingEntry.class))
            .toList());
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
    return transaction(tx -> {
      var current = read(tx, "Replay", request.id(), ReplayAudit.class);
      if (current != null) {
        if (!sameSelection(current, request)) {
          throw new IllegalArgumentException("Replay selection conflict");
        }
        return current;
      }
      save(tx, "Replay", request.id(), request.stage(), request);
      return request;
    });
  }

  @Override
  public Optional<ReplayAudit> replay(String id) {
    return transaction(
        manager -> Optional.ofNullable(read(manager, "Replay", id, ReplayAudit.class)));
  }

  @Override
  public void replayProgress(String id, long nextOffset, boolean finished, String failure) {
    transaction(tx -> {
      var current = read(tx, "Replay", id, ReplayAudit.class);
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
      save(tx, "Replay", id, current.stage(), next);
      return true;
    });
  }

  @Override
  public boolean reopen(String id, String requestId, Instant now) {
    return transaction(tx -> {
      var audit = read(tx, "Replay", requestId, ReplayAudit.class);
      var current = read(tx, "Entry", id, ProcessingEntry.class);
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
        save(tx, "Entry", id, next.stage(), next);
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
      save(tx, "Entry", id, next.stage(), next);
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
    return transaction(manager -> {
      ProcessingEntry previous = read(manager, "Entry", id, ProcessingEntry.class);
      ProcessingEntry next = update.apply(previous);
      if (next != null && next != previous) {
        save(manager, "Entry", id, next.stage(), next);
      }
      return result.apply(previous, next);
    });
  }

  private <T> T transaction(Function<EntityManager, T> action) {
    for (int attempt = 0; attempt < 5; attempt++) {
      try (EntityManager manager = factory.createEntityManager()) {
        var transaction = manager.getTransaction();
        try {
          transaction.begin();
          T result = action.apply(manager);
          transaction.commit();
          return result;
        } finally {
          if (transaction.isActive()) {
            transaction.rollback();
          }
        }
      } catch (IllegalArgumentException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        if (attempt == 4) {
          throw new ProcessingInfrastructureException();
        }
      }
    }
    throw new ProcessingInfrastructureException();
  }

  private <T> T read(EntityManager manager, String kind, String id, Class<T> type) {
    return decode(
        manager.find(
            ProcessingStateEntity.class,
            key(kind, id),
            LockModeType.PESSIMISTIC_WRITE,
            Map.of("jakarta.persistence.lock.timeout", 1000)),
        type);
  }

  private <T> T decode(ProcessingStateEntity entity, Class<T> type) {
    if (entity == null) {
      return null;
    }
    T value = mapper.readValue(entity.snapshot(), type);
    if (value instanceof ProcessingEntry entry && entry.recordVersion() != 1
        || value instanceof ReplayAudit audit && audit.recordVersion() != 1) {
      throw new ProcessingInfrastructureException();
    }
    return value;
  }

  private void save(EntityManager manager, String kind, String id, String stage, Object value) {
    String snapshot = mapper.writeValueAsString(value);
    if (snapshot.getBytes(StandardCharsets.UTF_8).length > maxStoredOutcomeBytes()) {
      throw new IllegalArgumentException("Processing state exceeds capacity");
    }
    String key = key(kind, id);
    var entity =
        manager.find(
            ProcessingStateEntity.class,
            key,
            LockModeType.PESSIMISTIC_WRITE,
            Map.of("jakarta.persistence.lock.timeout", 1000));
    boolean fresh = entity == null;
    if (fresh) {
      entity = new ProcessingStateEntity();
      entity.id(key);
    }
    entity.kind(kind);
    entity.stage(stage);
    entity.pending(
        value instanceof ProcessingEntry entry
            && entry.outputs().stream().anyMatch(output -> !output.published()));
    entity.snapshot(snapshot);
    if (fresh) {
      manager.persist(entity);
    }
    manager.flush();
  }

  private static String key(String kind, String id) {
    if (id == null || id.isBlank() || id.length() > 700) {
      throw new IllegalArgumentException("Invalid processing record identity");
    }
    return kind + ":" + id;
  }

  @FunctionalInterface
  private interface Result<T> {
    T apply(ProcessingEntry previous, ProcessingEntry next);
  }
}
