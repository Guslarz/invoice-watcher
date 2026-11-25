package com.guslarz.invoicewatcher.dao.mailattachment.api.service;

import com.guslarz.invoicewatcher.dao.mailattachment.api.model.AttachmentCapture;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import java.time.Instant;

/** Each mutation is atomic and fences stale capture attempts. */
public interface AttachmentCaptureDao {
  AttachmentCapture acquire(
      String id,
      String ownerScope,
      String sourceId,
      Instant now,
      Instant availableUntil,
      Instant leaseUntil);

  AttachmentCapture find(String id);

  void complete(AttachmentCapture capture, AttachmentManifest manifest, Instant now);

  /** Records an ownership-checked deletion marker; blob lifecycle remains independent. */
  void markDeleted(String id, String ownerScope, Instant now);

  void expire(Instant now, int limit);
}
