package com.guslarz.invoicewatcher.attachmentstorage.filesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.guslarz.invoicewatcher.attachmentstorage.filesystem.properties.FilesystemStorageProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemAttachmentStorageTest {
  private static final String OWNER = "a".repeat(64);
  private static final String KEY = OWNER + "/" + "b".repeat(64);
  private final byte[] content = "untrusted-pdf".getBytes(StandardCharsets.UTF_8);
  @TempDir private Path temporary;
  private final MutableClock clock = new MutableClock();

  @Test
  @DisplayName("ATTACHMENT-013: duplicate capture and restart preserve one immutable generation")
  void reopensImmutableObjectsWithoutOverwritingConflicts() throws Exception {
    // given
    String generation;
    try (var uut = backend()) {
      // when
      generation = put(uut).generation();
      var repeated = put(uut);
      var conflict =
          catchThrowable(() ->
              uut.put(
                  KEY,
                  OWNER,
                  new byte[] {1},
                  digest(new byte[] {1}),
                  clock.instant(),
                  clock.instant().plus(Duration.ofDays(9))));
      // then
      assertThat(repeated.generation()).isEqualTo(generation);
      assertThat(conflict).hasMessage("evidence-integrity-failure");
      assertThat(uut.read(KEY, generation, content.length)).isEqualTo(content);
    }
    // when
    try (var uut = backend()) {
      var repeated = put(uut);
      // then
      assertThat(repeated.generation()).isEqualTo(generation);
      assertThat(uut.read(KEY, generation, content.length)).isEqualTo(content);
      assertThat(Files.getPosixFilePermissions(directory()))
          .isEqualTo(PosixFilePermissions.fromString("rwx------"));
      assertThat(Files.getPosixFilePermissions(object()))
          .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
  }

  @Test
  @DisplayName(
      "ATTACHMENT-013: concurrent writers reuse one object and another root owner is rejected")
  void serializesConcurrentPublicationAndLocksTheRoot() throws Exception {
    // given
    try (var uut = backend();
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<String>> writes = new ArrayList<>();
      // when
      for (int i = 0; i < 8; i++) {
        writes.add(executor.submit(() -> put(uut).generation()));
      }
      List<String> generations = new ArrayList<>();
      for (var write : writes) {
        generations.add(write.get());
      }
      var anotherOwner = catchThrowable(this::backend);
      // then
      assertThat(generations).containsOnly(generations.getFirst());
      assertThat(anotherOwner)
          .hasMessageContaining("attachment-storage.filesystem.directory")
          .hasNoCause();
    }
  }

  @Test
  @DisplayName("ATTACHMENT-013: publication failure leaves no readable partial final object")
  void recoversAfterPublicationFailure() throws Exception {
    // given
    try (var uut =
        new FilesystemAttachmentStorage(
            properties(Duration.ofHours(1)),
            clock,
            (target, staged) -> {
              throw new IOException("private-path");
            })) {
      // when
      var failed = catchThrowable(() -> put(uut));
      // then
      assertThat(failed).hasMessage("attachment-storage-unavailable").hasNoCause();
      assertThat(object()).doesNotExist();
    }
    // when
    try (var uut = backend()) {
      var stored = put(uut);
      // then
      assertThat(uut.read(KEY, stored.generation(), content.length)).isEqualTo(content);
    }
  }

  @Test
  @DisplayName(
      "ATTACHMENT-013 ATTACHMENT-014: reads distinguish expired, missing and corrupted evidence")
  void enforcesExpiryGenerationAndIntegrity() throws Exception {
    // given
    try (var uut = backend()) {
      String generation = put(uut).generation();
      // when
      var wrongGeneration = catchThrowable(() -> uut.read(KEY, UUID.randomUUID().toString(), 100));
      final var tooSmall = catchThrowable(() -> uut.read(KEY, generation, 1));
      clock.advance(Duration.ofDays(9));
      final var expired = catchThrowable(() -> uut.read(KEY, generation, 100));
      Files.write(object(), new byte[] {1});
      var corrupted = catchThrowable(() -> uut.read(KEY, generation, 100));
      // then
      assertThat(wrongGeneration).hasMessage("evidence-missing");
      assertThat(tooSmall).hasMessage("evidence-integrity-failure");
      assertThat(expired).hasMessage("evidence-expired");
      assertThat(corrupted).hasMessage("evidence-integrity-failure");
    }
  }

  @Test
  @DisplayName(
      "ATTACHMENT-013: traversal, symlink files and symlink owner directories cannot escape root")
  void rejectsUnsafePathsAndPreservesOutsideFiles() throws Exception {
    // given
    Path outside = temporary.resolve("outside.txt");
    Files.writeString(outside, "private-content");
    try (var uut = backend()) {
      String generation = put(uut).generation();
      Files.delete(object());
      Files.createSymbolicLink(object(), outside);
      // when
      var traversal = catchThrowable(() -> uut.read("../outside.txt", generation, 100));
      var symlink = catchThrowable(() -> uut.read(KEY, generation, 100));
      uut.cleanupExpired();
      // then
      assertThat(traversal).hasMessage("invalid-evidence-reference");
      assertThat(symlink).hasMessage("invalid-evidence-reference");
      assertThat(Files.readString(outside)).isEqualTo("private-content");
    }
    Files.delete(object());
    Files.delete(directory().resolve(OWNER));
    Files.createSymbolicLink(directory().resolve(OWNER), temporary);
    // when
    try (var uut = backend()) {
      var failed = catchThrowable(() -> put(uut));
      // then
      assertThat(failed).hasMessage("attachment-storage-unavailable");
      assertThat(Files.readString(outside)).isEqualTo("private-content");
    }
  }

  @Test
  @DisplayName("ATTACHMENT-014: startup removes stale staging and expired orphaned objects only")
  void recoversStagingAndExpiresOrphansOnRestart() throws Exception {
    // given
    try (var uut = backend()) {
      put(uut);
    }
    Path abandoned = directory().resolve(OWNER).resolve(".staging-" + UUID.randomUUID() + ".tmp");
    Files.writeString(abandoned, "partial");
    Path unrelated = directory().resolve(OWNER).resolve("keep.txt");
    Files.writeString(unrelated, "unrelated");
    clock.advance(Duration.ofDays(10));
    // when
    try (var uut = backend()) {
      // then
      assertThat(object()).doesNotExist();
      assertThat(abandoned).doesNotExist();
      assertThat(Files.readString(unrelated)).isEqualTo("unrelated");
      assertThat(uut.storeId()).isEqualTo("local-attachments");
    }
  }

  @Test
  @DisplayName("ATTACHMENT-014: periodic cleanup runs independently of Kafka and processing")
  void cleansExpiredEntriesWhileRunning() throws Exception {
    // given
    try (var uut = new FilesystemAttachmentStorage(properties(Duration.ofSeconds(1)), clock)) {
      put(uut);
      // when
      clock.advance(Duration.ofDays(10));
      await().atMost(Duration.ofSeconds(5)).until(() -> !Files.exists(object()));
      // then
      assertThat(object()).doesNotExist();
    }
  }

  @Test
  @DisplayName("ATTACHMENT-013: an insecure configured root fails before storage starts")
  void rejectsInsecureRoots() throws Exception {
    // given
    Files.createDirectory(directory());
    Files.setPosixFilePermissions(directory(), PosixFilePermissions.fromString("rwxr-xr-x"));
    // when
    var failed = catchThrowable(this::backend);
    // then
    assertThat(failed).hasMessageContaining("attachment-storage.filesystem.directory").hasNoCause();
  }

  private FilesystemAttachmentStorage backend() {
    return new FilesystemAttachmentStorage(properties(Duration.ofHours(1)), clock);
  }

  private FilesystemStorageProperties properties(Duration interval) {
    return new FilesystemStorageProperties(directory(), "local-attachments", interval);
  }

  private Path directory() {
    return temporary.resolve("objects");
  }

  private Path object() {
    return directory().resolve(KEY + ".blob");
  }

  private com.guslarz.invoicewatcher.attachmentstorage.api.model.StoredAttachment put(
      FilesystemAttachmentStorage storage) throws Exception {
    return storage.put(
        KEY,
        OWNER,
        content,
        digest(content),
        clock.instant(),
        clock.instant().plus(Duration.ofDays(9)));
  }

  private static String digest(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static class MutableClock extends Clock {
    private volatile Instant now = Instant.parse("2026-11-25T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
