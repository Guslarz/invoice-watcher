package com.guslarz.invoicewatcher.dao.mailattachment.firestore.service;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.Transaction;
import com.guslarz.invoicewatcher.dao.mailattachment.api.model.AttachmentCapture;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import java.time.Instant;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public final class FirestoreAttachmentCaptureDao implements AttachmentCaptureDao {
  private static final Pattern CAPTURE_ID = Pattern.compile("[a-f0-9]{64}");
  private static final String KIND = "attachment-captures";

  @Qualifier("attachmentDatastore")
  private final Datastore datastore;

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
    try {
      return decode(datastore.get(key(id)));
    } catch (RuntimeException exception) {
      throw safe(exception);
    }
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
    try {
      var query =
          Query.newEntityQueryBuilder()
              .setKind(KIND)
              .setFilter(PropertyFilter.le("expiresAt", timestamp(now)))
              .setLimit(limit)
              .build();
      var results = datastore.run(query);
      while (results.hasNext()) {
        String id = results.next().getKey().getName();
        mutate(
            id,
            old ->
                old == null || old.availableUntil().isAfter(now)
                    ? old
                    : old.toBuilder().manifest(null).expired(true).build());
      }
    } catch (RuntimeException exception) {
      throw safe(exception);
    }
  }

  private AttachmentCapture mutate(String id, UnaryOperator<AttachmentCapture> change) {
    try {
      return mutateTransaction(id, change);
    } catch (RuntimeException exception) {
      throw safe(exception);
    }
  }

  private AttachmentCapture mutateTransaction(String id, UnaryOperator<AttachmentCapture> change) {
    Transaction transaction = datastore.newTransaction();
    try {
      Key key = key(id);
      AttachmentCapture next = change.apply(decode(transaction.get(key)));
      if (next != null) {
        byte[] bytes = mapper.writeValueAsBytes(next);
        var entity =
            Entity.newBuilder(key)
                .set(
                    "record",
                    BlobValue.newBuilder(Blob.copyFrom(bytes)).setExcludeFromIndexes(true).build());
        // Terminal suppression records are deliberately absent from the expiry query index.
        if (!next.expired() && next.deletedAt() == null) {
          entity.set("expiresAt", timestamp(next.availableUntil()));
        }
        transaction.put(entity.build());
      }
      transaction.commit();
      return next;
    } catch (RuntimeException exception) {
      throw safe(exception);
    } finally {
      if (transaction.isActive()) {
        transaction.rollback();
      }
    }
  }

  private Key key(String id) {
    if (id == null || !CAPTURE_ID.matcher(id).matches()) {
      throw new AttachmentException("invalid-capture-identity");
    }
    return datastore.newKeyFactory().setKind(KIND).newKey(id);
  }

  private AttachmentCapture decode(Entity entity) {
    return entity == null
        ? null
        : mapper.readValue(entity.getBlob("record").toByteArray(), AttachmentCapture.class);
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
  }

  private static AttachmentException safe(RuntimeException exception) {
    return exception instanceof AttachmentException safe
        ? safe
        : new AttachmentException("capture-storage-unavailable");
  }
}
