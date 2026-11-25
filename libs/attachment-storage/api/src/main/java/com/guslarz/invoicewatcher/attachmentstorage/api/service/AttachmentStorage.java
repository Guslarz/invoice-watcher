package com.guslarz.invoicewatcher.attachmentstorage.api.service;

import com.guslarz.invoicewatcher.attachmentstorage.api.model.StoredAttachment;
import java.time.Instant;

/** Private storage; callers must authorize application-user ownership before reading. */
public interface AttachmentStorage {
  String storeId();

  StoredAttachment put(
      String key,
      String ownerScope,
      byte[] bytes,
      String sha256,
      Instant capturedAt,
      Instant availableUntil);

  byte[] read(String key, String generation, long maxBytes);
}
