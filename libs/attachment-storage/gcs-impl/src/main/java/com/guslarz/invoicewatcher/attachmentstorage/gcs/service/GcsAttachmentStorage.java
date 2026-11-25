package com.guslarz.invoicewatcher.attachmentstorage.gcs.service;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.guslarz.invoicewatcher.attachmentstorage.api.model.StoredAttachment;
import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.attachmentstorage.gcs.properties.AttachmentGcsProperties;
import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class GcsAttachmentStorage implements AttachmentStorage {
  private static final Pattern OBJECT_KEY = Pattern.compile("[a-f0-9]{64}/[a-f0-9]{64}");
  private static final Pattern GENERATION = Pattern.compile("[1-9][0-9]{0,18}");

  @Qualifier("attachmentGcsClient")
  private final Storage client;

  private final AttachmentGcsProperties properties;

  @Override
  public String storeId() {
    return properties.storeId();
  }

  @Override
  public StoredAttachment put(
      String key,
      String ownerScope,
      byte[] bytes,
      String sha256,
      Instant capturedAt,
      Instant availableUntil) {
    validKey(key);
    Map<String, String> metadata =
        Map.of(
            "ownerScope",
            ownerScope,
            "sha256",
            sha256,
            "capturedAt",
            capturedAt.toString(),
            "availableUntil",
            availableUntil.toString());
    BlobInfo info =
        BlobInfo.newBuilder(properties.bucket(), key)
            .setMetadata(metadata)
            .setContentType("application/octet-stream")
            .build();
    try {
      verifyBucket(Duration.between(capturedAt, availableUntil));
      Blob object;
      boolean reused = false;
      try {
        object = client.create(info, bytes, Storage.BlobTargetOption.doesNotExist());
      } catch (StorageException conflict) {
        if (conflict.getCode() != 412) {
          throw conflict;
        }
        object = client.get(BlobId.of(properties.bucket(), key));
        reused = true;
      }
      if (object == null
          || object.getSize() != bytes.length
          || !metadata.equals(object.getMetadata())) {
        throw new AttachmentException("evidence-integrity-failure");
      }
      if (reused
          && !Arrays.equals(bytes, read(key, object.getGeneration().toString(), bytes.length))) {
        throw new AttachmentException("evidence-integrity-failure");
      }
      return new StoredAttachment(object.getGeneration().toString(), sha256, bytes.length);
    } catch (StorageException exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public byte[] read(String key, String generation, long maxBytes) {
    validKey(key);
    if (maxBytes < 0
        || maxBytes > 16777216
        || generation == null
        || !GENERATION.matcher(generation).matches()) {
      throw new AttachmentException("invalid-evidence-reference");
    }
    try (ReadChannel reader =
        client.reader(BlobId.of(properties.bucket(), key, Long.parseLong(generation)))) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ByteBuffer buffer = ByteBuffer.allocate(8192);
      while (true) {
        buffer.clear();
        int read = reader.read(buffer);
        if (read < 0) {
          return output.toByteArray();
        }
        if (output.size() + read > maxBytes) {
          throw new AttachmentException("evidence-integrity-failure");
        }
        output.write(buffer.array(), 0, read);
      }
    } catch (StorageException exception) {
      throw storageFailure(exception);
    } catch (IOException exception) {
      throw new AttachmentException("attachment-storage-unavailable");
    }
  }

  private void verifyBucket(Duration lifetime) {
    Bucket bucket = client.get(properties.bucket());
    long wholeDays = lifetime.toDays();
    int days = Math.toIntExact(wholeDays + (lifetime.minusDays(wholeDays).isZero() ? 0 : 1));
    if (bucket == null
        || bucket.getIamConfiguration() == null
        || !Boolean.TRUE.equals(bucket.getIamConfiguration().isUniformBucketLevelAccessEnabled())
        || !BucketInfo.PublicAccessPrevention.ENFORCED.equals(
            bucket.getIamConfiguration().getPublicAccessPrevention())
        || Boolean.TRUE.equals(bucket.versioningEnabled())
        || bucket.getSoftDeletePolicy() != null
            && !Duration.ZERO.equals(bucket.getSoftDeletePolicy().getRetentionDuration())
        || bucket.getRetentionPeriodDuration() != null
            && !bucket.getRetentionPeriodDuration().isZero()
        || bucket.getLifecycleRules() == null
        || bucket.getLifecycleRules().size() != 1) {
      throw new AttachmentException("unsuitable-attachment-bucket-policy");
    }
    var rule = bucket.getLifecycleRules().getFirst();
    var expected = BucketInfo.LifecycleRule.LifecycleCondition.newBuilder().setAge(days).build();
    if (!"Delete".equals(rule.getAction().getActionType())
        || !expected.equals(rule.getCondition())) {
      throw new AttachmentException("unsuitable-attachment-bucket-policy");
    }
  }

  private static void validKey(String key) {
    if (key == null || !OBJECT_KEY.matcher(key).matches()) {
      throw new AttachmentException("invalid-evidence-reference");
    }
  }

  private static AttachmentException storageFailure(StorageException exception) {
    return new AttachmentException(
        switch (exception.getCode()) {
          case 404 -> "evidence-missing";
          case 403 -> "evidence-unauthorized";
          default -> "attachment-storage-unavailable";
        });
  }
}
