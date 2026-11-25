package com.guslarz.invoicewatcher.mailattachment.impl.service;

import com.guslarz.invoicewatcher.attachmentstorage.api.model.StoredAttachment;
import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.dao.mailattachment.api.model.AttachmentCapture;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentDescriptor;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentEvidence;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import com.guslarz.invoicewatcher.mailattachment.api.properties.MailAttachmentProperties;
import com.guslarz.invoicewatcher.mailattachment.api.service.AttachmentPart;
import com.guslarz.invoicewatcher.mailattachment.api.service.MailAttachmentService;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
public final class DefaultMailAttachmentService implements MailAttachmentService {
  private static final Pattern SOURCE_ID = Pattern.compile("imap-v1:[0-9a-f]{64}");
  private final MailAttachmentProperties properties;
  private final AttachmentStorage storage;
  private final AttachmentCaptureDao dao;
  private final Clock clock;
  private final MeterRegistry meters;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Override
  public AttachmentManifest capture(String user, String sourceId, AttachmentPart root) {
    FutureTask<AttachmentManifest> task = new FutureTask<>(() -> captureNow(user, sourceId, root));
    Thread.ofVirtual().name("attachment-capture").start(task);
    try {
      return task.get(properties.captureTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      task.cancel(true);
      Thread.currentThread().interrupt();
      throw recordFailure(failure("capture-interrupted"));
    } catch (TimeoutException exception) {
      task.cancel(true);
      throw recordFailure(failure("capture-time-limit"));
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof AttachmentException safe) {
        throw recordFailure(safe);
      }
      throw recordFailure(failure("capture-infrastructure-failure"));
    }
  }

  private AttachmentManifest captureNow(String user, String sourceId, AttachmentPart root) {
    validateSource(sourceId);
    String scope = AttachmentIdentity.create("attachment-owner-v1", user);
    String id = AttachmentIdentity.create("attachment-capture-v1", user, sourceId);
    Instant started = clock.instant();
    AttachmentCapture record =
        dao.acquire(
            id,
            scope,
            sourceId,
            started,
            started.plus(properties.evidenceLifetime()),
            started.plus(properties.captureTimeout()).plusSeconds(10));
    if (record.deletedAt() != null) {
      throw failure("evidence-deleted");
    }
    if (record.expired() || !record.availableUntil().isAfter(started)) {
      throw failure("evidence-expired");
    }
    if (record.manifest() != null) {
      count("reused");
      return record.manifest();
    }
    CaptureWork work = new CaptureWork(record, started.plus(properties.captureTimeout()));
    if (root != null) {
      work.visit(root, "1", 1);
    }
    work.check();
    AttachmentManifest manifest = work.manifest();
    dao.complete(record, manifest, clock.instant());
    return manifest;
  }

  @Override
  public void requirePublicationWindow(AttachmentManifest manifest, Duration inputRetention) {
    if (inputRetention == null || !inputRetention.equals(properties.supportedReplayHorizon())) {
      throw recordFailure(failure("incompatible-input-retention"));
    }
    Instant minimum =
        clock
            .instant()
            .plus(properties.supportedReplayHorizon())
            .plus(properties.recoveryMargin())
            .plus(properties.sendTimeout());
    for (AttachmentDescriptor item : manifest.attachments()) {
      if (item.evidence() != null && !item.evidence().availableUntil().isAfter(minimum)) {
        throw recordFailure(failure("insufficient-publication-window"));
      }
    }
  }

  @Override
  public byte[] read(String user, String sourceId, AttachmentDescriptor attachment) {
    try {
      return readEvidence(user, sourceId, attachment);
    } catch (AttachmentException exception) {
      throw recordFailure(exception);
    }
  }

  private byte[] readEvidence(String user, String sourceId, AttachmentDescriptor attachment) {
    validateSource(sourceId);
    String scope = AttachmentIdentity.create("attachment-owner-v1", user);
    String id = AttachmentIdentity.create("attachment-capture-v1", user, sourceId);
    AttachmentCapture record = dao.find(id);
    if (record == null
        || !record.ownerScope().equals(scope)
        || !record.sourceId().equals(sourceId)) {
      throw failure("evidence-unauthorized");
    }
    if (record.deletedAt() != null) {
      throw failure("evidence-deleted");
    }
    if (record.expired() || !record.availableUntil().isAfter(clock.instant())) {
      throw failure("evidence-expired");
    }
    if (record.manifest() == null
        || !record.manifest().attachments().contains(attachment)
        || attachment.evidence() == null) {
      throw failure("evidence-unauthorized");
    }
    AttachmentEvidence evidence = attachment.evidence();
    if (!storage.storeId().equals(evidence.store())) {
      throw failure("evidence-unauthorized");
    }
    byte[] bytes = storage.read(evidence.objectKey(), evidence.generation(), evidence.byteLength());
    if (bytes.length != evidence.byteLength()
        || !AttachmentIdentity.digest(bytes).equals(evidence.sha256())) {
      throw failure("evidence-integrity-failure");
    }
    return bytes;
  }

  @Scheduled(fixedDelay = 60000)
  public void expireMetadata() {
    try {
      dao.expire(clock.instant(), 100);
    } catch (RuntimeException exception) {
      count("cleanup-failed");
    }
  }

  private static void validateSource(String sourceId) {
    if (sourceId == null || !SOURCE_ID.matcher(sourceId).matches()) {
      throw new AttachmentException("invalid-source-identity");
    }
  }

  private void count(String outcome) {
    meters.counter("invoice.watcher.attachments", "outcome", outcome).increment();
  }

  private AttachmentException recordFailure(AttachmentException exception) {
    count("evidence-expired".equals(exception.getMessage()) ? "expired" : "failed");
    return exception;
  }

  private static AttachmentException failure(String reason) {
    return new AttachmentException(reason);
  }

  private final class CaptureWork {
    private final AttachmentCapture record;
    private final Instant deadline;
    private final List<AttachmentDescriptor> items = new ArrayList<>();
    private int bytesExamined;
    private int nodes;
    private String limit;

    CaptureWork(AttachmentCapture record, Instant deadline) {
      this.record = record;
      this.deadline = deadline;
    }

    void check() {
      if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline)) {
        throw failure("capture-time-limit");
      }
    }

    void visit(AttachmentPart part, String path, int depth) {
      check();
      if (limit != null || part.attachedMessage()) {
        return;
      }
      if (depth > properties.maxMimeDepth()) {
        limit = "mime-depth-limit";
        return;
      }
      if (++nodes > properties.maxCandidates() * properties.maxMimeDepth() * 4) {
        limit = "mime-node-limit";
        return;
      }
      if (part.candidate()) {
        candidate(part, path);
        return;
      }
      int children = part.childCount();
      for (int index = 0; index < children && limit == null; index++) {
        visit(part.child(index), path + "." + (index + 1), depth + 1);
      }
    }

    void candidate(AttachmentPart part, String path) {
      if (items.size() >= properties.maxCandidates()) {
        limit = "candidate-count-limit";
        return;
      }
      String type = part.mediaType();
      // Bound even hostile source adapters before materializing a manifest.
      if (type == null || type.length() > 128) {
        type = "application/octet-stream";
      }
      String identity =
          "attachment-v1:"
              + AttachmentIdentity.create(
                  "attachment-v1", record.ownerScope(), record.sourceId(), path);
      AttachmentDescriptor descriptor =
          new AttachmentDescriptor(identity, path, type, "unsupported-type", null);
      if ("application/pdf".equals(type)) {
        descriptor = readCandidate(part, descriptor);
      } else {
        count("unsupported");
      }
      List<AttachmentDescriptor> proposed = new ArrayList<>(items);
      proposed.add(descriptor);
      AttachmentManifest test = new AttachmentManifest(1, true, null, proposed);
      // Reserve space for a global limit reason and complete=false.
      if (mapper.writeValueAsBytes(test).length + 128 > properties.maxManifestBytes()) {
        limit = "manifest-size-limit";
      } else {
        items.add(descriptor);
      }
    }

    AttachmentDescriptor readCandidate(AttachmentPart part, AttachmentDescriptor descriptor) {
      try (InputStream input = part.openStream()) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
          check();
          int remaining =
              Math.min(
                  properties.maxAttachmentBytes() - output.size(),
                  properties.maxTotalBytes() - bytesExamined);
          int read = input.read(buffer, 0, Math.min(buffer.length, remaining + 1));
          check();
          if (read < 0) {
            break;
          }
          bytesExamined += read;
          if (bytesExamined > properties.maxTotalBytes()) {
            limit = "total-byte-limit";
            return limited(descriptor);
          }
          if (output.size() + read > properties.maxAttachmentBytes()) {
            return limited(descriptor);
          }
          output.write(buffer, 0, read);
        }
        byte[] bytes = output.toByteArray();
        String digest = AttachmentIdentity.digest(bytes);
        String key = record.ownerScope() + "/" + descriptor.attachmentId().substring(14);
        check();
        StoredAttachment stored =
            storage.put(
                key,
                record.ownerScope(),
                bytes,
                digest,
                record.capturedAt(),
                record.availableUntil());
        check();
        if (!stored.sha256().equals(digest) || stored.byteLength() != bytes.length) {
          throw failure("evidence-integrity-failure");
        }
        AttachmentEvidence evidence =
            AttachmentEvidence.builder()
                .store(storage.storeId())
                .objectKey(key)
                .generation(stored.generation())
                .sha256(digest)
                .byteLength(bytes.length)
                .capturedAt(record.capturedAt())
                .availableUntil(record.availableUntil())
                .build();
        count("captured");
        return new AttachmentDescriptor(
            descriptor.attachmentId(),
            descriptor.partPath(),
            descriptor.declaredContentType(),
            "stored",
            evidence);
      } catch (IOException exception) {
        throw failure("mime-decoding-failure");
      }
    }

    AttachmentDescriptor limited(AttachmentDescriptor descriptor) {
      count("limited");
      return new AttachmentDescriptor(
          descriptor.attachmentId(),
          descriptor.partPath(),
          descriptor.declaredContentType(),
          "size-limit",
          null);
    }

    AttachmentManifest manifest() {
      return new AttachmentManifest(1, limit == null, limit, items);
    }
  }
}
