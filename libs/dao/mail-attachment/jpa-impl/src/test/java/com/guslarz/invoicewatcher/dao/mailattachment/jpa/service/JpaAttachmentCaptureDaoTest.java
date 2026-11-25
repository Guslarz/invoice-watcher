package com.guslarz.invoicewatcher.dao.mailattachment.jpa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class JpaAttachmentCaptureDaoTest {
  @TempDir Path temporary;
  private AnnotationConfigApplicationContext context;

  private AttachmentCaptureDao open() {
    context = new AnnotationConfigApplicationContext();
    context.register(TestApplication.class);
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "invoice-watcher.mail-attachment.dao.mode", "jpa",
                    "invoice-watcher.mail-attachment.dao.jpa.data-source.basic.url",
                        "jdbc:h2:file:" + temporary.resolve("captures"),
                    "invoice-watcher.mail-attachment.dao.jpa.data-source.basic.username", "sa",
                    "invoice-watcher.mail-attachment.dao.jpa.data-source.basic.driver",
                        "org.h2.Driver",
                    "invoice-watcher.mail-attachment.dao.jpa.ddl-auto", "update",
                    "invoice-watcher.mail-attachment.dao.jpa.dialect",
                        "org.hibernate.dialect.H2Dialect")));
    context.refresh();
    return context.getBean(AttachmentCaptureDao.class);
  }

  private AttachmentCaptureDao restart() {
    context.close();
    return open();
  }

  @AfterEach
  void close() {
    if (context != null) {
      context.close();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}

  @Test
  @DisplayName(
      "ATTACHMENT-015 ATTACHMENT-009 ATTACHMENT-010: deletion markers preserve ownership and fence"
          + " in-flight capture")
  void persistsOwnershipCheckedDeletionMarkers() {
    // given
    var uut = open();
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
    var restarted = restart();
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
      "ATTACHMENT-015 ATTACHMENT-006 ATTACHMENT-010: leases fence stale completion and expiry"
          + " survives restart")
  void preservesReservationsFencesAndExpiry() {
    // given
    var uut = open();
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
    var restarted = restart();
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

  @Test
  @DisplayName("ATTACHMENT-015: concurrent first reservations have one durable winner")
  void serializesConcurrentAdmission() throws Exception {
    // given
    var uut = open();
    Instant now = Instant.parse("2026-11-24T00:00:00Z");
    String id = "c".repeat(64);
    // when
    var results = new ArrayList<Object>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          java.util.stream.IntStream.range(0, 8)
              .mapToObj(index ->
                  executor.submit(() -> {
                    try {
                      return (Object)
                          uut.acquire(
                              id,
                              "owner",
                              "source",
                              now,
                              now.plusSeconds(100),
                              now.plusSeconds(10));
                    } catch (RuntimeException exception) {
                      return (Object) exception;
                    }
                  }))
              .toList();
      for (var task : tasks) {
        results.add(task.get());
      }
    }
    var stored = restart().find(id);
    // then
    assertThat(results.stream().filter(value -> !(value instanceof Throwable))).hasSize(1);
    assertThat(results.stream().filter(Throwable.class::isInstance).map(Throwable.class::cast))
        .allSatisfy(error -> assertThat(error).hasMessage("capture-busy"));
    assertThat(stored.fence()).isEqualTo(1);
  }
}
