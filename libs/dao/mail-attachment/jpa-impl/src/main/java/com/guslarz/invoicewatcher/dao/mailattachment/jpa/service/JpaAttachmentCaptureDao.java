package com.guslarz.invoicewatcher.dao.mailattachment.jpa.service;

import com.guslarz.invoicewatcher.dao.mailattachment.api.model.AttachmentCapture;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.dao.mailattachment.jpa.configuration.AttachmentJpaAutoConfiguration;
import com.guslarz.invoicewatcher.dao.mailattachment.jpa.entity.AttachmentCaptureEntity;
import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public final class JpaAttachmentCaptureDao implements AttachmentCaptureDao {
  private static final Pattern CAPTURE_ID = Pattern.compile("[a-f0-9]{64}");

  @Qualifier(AttachmentJpaAutoConfiguration.Beans.ENTITY_MANAGER_FACTORY)
  private final EntityManagerFactory factory;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Override
  public AttachmentCapture acquire(
      String id,
      String ownerScope,
      String sourceId,
      Instant now,
      Instant availableUntil,
      Instant leaseUntil) {
    return mutate(
        id,
        old -> {
          if (old == null) {
            return AttachmentCapture.builder()
                .id(id)
                .ownerScope(ownerScope)
                .sourceId(sourceId)
                .policyVersion(1)
                .capturedAt(now)
                .availableUntil(availableUntil)
                .fence(1)
                .leaseUntil(leaseUntil)
                .expired(false)
                .build();
          }
          if (!old.ownerScope().equals(ownerScope)
              || !old.sourceId().equals(sourceId)
              || old.policyVersion() != 1) {
            throw new AttachmentException("capture-integrity-failure");
          }
          if (old.deletedAt() != null) {
            throw new AttachmentException("evidence-deleted");
          }
          if (!old.availableUntil().isAfter(now) || old.expired()) {
            throw new AttachmentException("evidence-expired");
          }
          if (old.manifest() != null) {
            return old;
          }
          if (old.leaseUntil().isAfter(now)) {
            throw new AttachmentException("capture-busy");
          }
          return old.toBuilder().fence(old.fence() + 1).leaseUntil(leaseUntil).build();
        });
  }

  @Override
  public AttachmentCapture find(String id) {
    validId(id);
    return transaction(manager -> decode(manager.find(AttachmentCaptureEntity.class, id)));
  }

  @Override
  public void complete(AttachmentCapture capture, AttachmentManifest manifest, Instant now) {
    mutate(
        capture.id(),
        old -> {
          if (old == null
              || old.expired()
              || old.deletedAt() != null
              || old.fence() != capture.fence()
              || !old.leaseUntil().isAfter(now)
              || !old.availableUntil().isAfter(now)
              || old.manifest() != null) {
            throw new AttachmentException("stale-capture");
          }
          return old.toBuilder().manifest(manifest).build();
        });
  }

  @Override
  public void markDeleted(String id, String ownerScope, Instant now) {
    mutate(
        id,
        old -> {
          if (old == null || !old.ownerScope().equals(ownerScope)) {
            throw new AttachmentException("evidence-unauthorized");
          }
          return old.deletedAt() != null
              ? old
              : old.toBuilder().deletedAt(now).manifest(null).fence(old.fence() + 1).build();
        });
  }

  @Override
  public void expire(Instant now, int limit) {
    var ids =
        transaction(manager ->
            manager
                .createQuery(
                    "select c.id from AttachmentCaptureEntity c where c.expiresAt <= :now",
                    String.class)
                .setParameter("now", now.toEpochMilli())
                .setMaxResults(limit)
                .getResultList());
    for (String id : ids) {
      mutate(
          id,
          old ->
              old == null || old.availableUntil().isAfter(now)
                  ? old
                  : old.toBuilder().manifest(null).expired(true).build());
    }
  }

  private AttachmentCapture mutate(String id, UnaryOperator<AttachmentCapture> change) {
    validId(id);
    return transaction(manager -> {
      var entity =
          manager.find(
              AttachmentCaptureEntity.class,
              id,
              LockModeType.PESSIMISTIC_WRITE,
              Map.of("jakarta.persistence.lock.timeout", 1000));
      AttachmentCapture previous = decode(entity);
      AttachmentCapture next = change.apply(previous);
      if (next != null && next != previous) {
        String snapshot = mapper.writeValueAsString(next);
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > 900000) {
          throw new AttachmentException("capture-state-too-large");
        }
        boolean fresh = entity == null;
        if (fresh) {
          entity = new AttachmentCaptureEntity();
          entity.id(id);
        }
        entity.snapshot(snapshot);
        entity.expiresAt(
            next.expired() || next.deletedAt() != null
                ? null
                : next.availableUntil().toEpochMilli());
        if (fresh) {
          manager.persist(entity);
        }
        manager.flush();
      }
      return next;
    });
  }

  private AttachmentCapture decode(AttachmentCaptureEntity entity) {
    if (entity == null) {
      return null;
    }
    var record = mapper.readValue(entity.snapshot(), AttachmentCapture.class);
    if (record.policyVersion() != 1) {
      throw new AttachmentException("capture-integrity-failure");
    }
    return record;
  }

  private <T> T transaction(Function<EntityManager, T> work) {
    for (int attempt = 0; attempt < 5; attempt++) {
      try (EntityManager manager = factory.createEntityManager()) {
        var transaction = manager.getTransaction();
        try {
          transaction.begin();
          T result = work.apply(manager);
          transaction.commit();
          return result;
        } finally {
          if (transaction.isActive()) {
            transaction.rollback();
          }
        }
      } catch (AttachmentException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        if (attempt == 4) {
          throw new AttachmentException("capture-storage-unavailable");
        }
      }
    }
    throw new AttachmentException("capture-storage-unavailable");
  }

  private static void validId(String id) {
    if (id == null || !CAPTURE_ID.matcher(id).matches()) {
      throw new AttachmentException("invalid-capture-identity");
    }
  }
}
