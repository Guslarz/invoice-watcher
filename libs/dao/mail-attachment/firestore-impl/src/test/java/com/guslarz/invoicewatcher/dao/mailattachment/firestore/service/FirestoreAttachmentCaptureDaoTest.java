package com.guslarz.invoicewatcher.dao.mailattachment.firestore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.DatastoreOptions;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import com.guslarz.invoicewatcher.testing.testcontainers.SharedTestcontainers;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.gcloud.DatastoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FirestoreAttachmentCaptureDaoTest {
  @Container private static DatastoreEmulatorContainer emulator = SharedTestcontainers.datastore();

  @Test
  @DisplayName(
      "ATTACHMENT-009 ATTACHMENT-010: deletion markers preserve ownership and fence in-flight"
          + " capture")
  void persistsOwnershipCheckedDeletionMarkers() {
    // given
    var datastore =
        DatastoreOptions.newBuilder()
            .setProjectId("attachment-test")
            .setNamespace(UUID.randomUUID().toString())
            .setHost(emulator.getEmulatorEndpoint())
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();
    var uut = new FirestoreAttachmentCaptureDao(datastore);
    Instant now = Instant.parse("2026-11-24T00:00:00Z");
    String id = "b".repeat(64);
    var capture =
        uut.acquire(id, "owner", "source", now, now.plusSeconds(100), now.plusSeconds(10));
    // when
    final var denied = catchThrowable(() -> uut.markDeleted(id, "another-owner", now));
    uut.markDeleted(id, "owner", now);
    final var stale =
        catchThrowable(
            () -> uut.complete(capture, new AttachmentManifest(1, true, null, List.of()), now));
    var restarted = new FirestoreAttachmentCaptureDao(datastore);
    var deleted = restarted.find(id);
    var recapture =
        catchThrowable(() ->
            restarted.acquire(
                id, "owner", "source", now, now.plusSeconds(200), now.plusSeconds(20)));
    // then
    assertThat(denied).hasMessage("evidence-unauthorized");
    assertThat(stale).hasMessage("stale-capture");
    assertThat(deleted.deletedAt()).isEqualTo(now);
    assertThat(deleted.manifest()).isNull();
    assertThat(recapture).hasMessage("evidence-deleted");
  }

  @Test
  @DisplayName(
      "ATTACHMENT-006 ATTACHMENT-010: leases fence stale completion and expiry survives restart")
  void preservesReservationsFencesAndExpiry() {
    // given
    var datastore =
        DatastoreOptions.newBuilder()
            .setProjectId("attachment-test")
            .setNamespace(UUID.randomUUID().toString())
            .setHost(emulator.getEmulatorEndpoint())
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();
    var uut = new FirestoreAttachmentCaptureDao(datastore);
    Instant now = Instant.parse("2026-11-24T00:00:00Z");
    String id = "a".repeat(64);
    var first = uut.acquire(id, "owner", "source", now, now.plusSeconds(100), now.plusSeconds(10));
    // when
    final var busy =
        catchThrowable(() ->
            uut.acquire(id, "owner", "source", now, now.plusSeconds(200), now.plusSeconds(20)));
    var next =
        uut.acquire(
            id, "owner", "source", now.plusSeconds(11), now.plusSeconds(200), now.plusSeconds(30));
    final var stale =
        catchThrowable(() ->
            uut.complete(
                first, new AttachmentManifest(1, true, null, List.of()), now.plusSeconds(12)));
    uut.complete(next, new AttachmentManifest(1, true, null, List.of()), now.plusSeconds(12));
    var restarted = new FirestoreAttachmentCaptureDao(datastore);
    final var complete = restarted.find(id);
    restarted.expire(now.plusSeconds(100), 100);
    var expired = restarted.find(id);
    var resurrected =
        catchThrowable(() ->
            restarted.acquire(
                id,
                "owner",
                "source",
                now.plusSeconds(101),
                now.plusSeconds(300),
                now.plusSeconds(120)));
    // then
    assertThat(busy).hasMessage("capture-busy");
    assertThat(stale).hasMessage("stale-capture");
    assertThat(next.fence()).isEqualTo(2);
    assertThat(next.availableUntil()).isEqualTo(first.availableUntil());
    assertThat(complete.manifest()).isNotNull();
    assertThat(expired.expired()).isTrue();
    assertThat(expired.manifest()).isNull();
    assertThat(resurrected).hasMessage("evidence-expired");
  }
}
