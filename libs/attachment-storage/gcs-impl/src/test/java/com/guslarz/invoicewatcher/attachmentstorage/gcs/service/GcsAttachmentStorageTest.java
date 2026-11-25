package com.guslarz.invoicewatcher.attachmentstorage.gcs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.guslarz.invoicewatcher.attachmentstorage.gcs.properties.AttachmentGcsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GcsAttachmentStorageTest {
  private final Storage client = mock(Storage.class);
  private final GcsAttachmentStorage uut =
      spy(
          new GcsAttachmentStorage(
              client,
              new AttachmentGcsProperties("private-test", "attachments", Duration.ofSeconds(15))));

  @Test
  @DisplayName(
      "ATTACHMENT-004 ATTACHMENT-005 ATTACHMENT-006: create-only conflict reconciles generation")
  void adoptsOnlyMatchingCreateOnlyObjects() {
    // given
    String key = "a".repeat(64) + "/" + "b".repeat(64);
    byte[] bytes = {1, 2};
    Instant captured = Instant.parse("2026-11-24T00:00:00Z");
    Instant expiry = captured.plus(Duration.ofDays(9));
    when(client.create(any(BlobInfo.class), eq(bytes), any(Storage.BlobTargetOption.class)))
        .thenThrow(new StorageException(412, "private-name"));
    Blob existing = mock(Blob.class);
    when(existing.getSize()).thenReturn(2L);
    when(existing.getGeneration()).thenReturn(42L);
    when(existing.getMetadata())
        .thenReturn(
            Map.of(
                "ownerScope",
                "owner",
                "sha256",
                "digest",
                "capturedAt",
                captured.toString(),
                "availableUntil",
                expiry.toString()));
    when(client.get(BlobId.of("private-test", key))).thenReturn(existing);
    var bucket = mock(Bucket.class);
    when(client.get("private-test")).thenReturn(bucket);
    when(bucket.getIamConfiguration())
        .thenReturn(
            BucketInfo.IamConfiguration.newBuilder()
                .setIsUniformBucketLevelAccessEnabled(true)
                .setPublicAccessPrevention(BucketInfo.PublicAccessPrevention.ENFORCED)
                .build());
    doReturn(
            List.of(
                new BucketInfo.LifecycleRule(
                    BucketInfo.LifecycleRule.LifecycleAction.newDeleteAction(),
                    BucketInfo.LifecycleRule.LifecycleCondition.newBuilder().setAge(9).build())))
        .when(bucket)
        .getLifecycleRules();
    doReturn(bytes).when(uut).read(key, "42", bytes.length);
    // when
    var result = uut.put(key, "owner", bytes, "digest", captured, expiry);
    // then
    assertThat(result.generation()).isEqualTo("42");
    verify(client)
        .create(any(BlobInfo.class), eq(bytes), eq(Storage.BlobTargetOption.doesNotExist()));
  }

  @Test
  @DisplayName(
      "ATTACHMENT-009 ATTACHMENT-011: arbitrary object keys fail without provider requests")
  void rejectsUnsafeReferences() {
    // given
    String unsafe = "https://private.example/secret.pdf";
    // when
    var failed = catchThrowable(() -> uut.read(unsafe, "1", 10));
    // then
    assertThat(failed).hasMessage("invalid-evidence-reference").hasNoCause();
  }
}
