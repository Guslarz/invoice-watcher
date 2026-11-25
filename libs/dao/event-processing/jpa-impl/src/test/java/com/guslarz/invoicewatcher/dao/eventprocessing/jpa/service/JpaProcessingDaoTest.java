package com.guslarz.invoicewatcher.dao.eventprocessing.jpa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingOutput;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingStatus;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ReplayAudit;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmissionDecision;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class JpaProcessingDaoTest {
  private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");
  @TempDir Path temporary;
  private AnnotationConfigApplicationContext context;

  private ProcessingDao open() {
    context = new AnnotationConfigApplicationContext();
    context.register(TestApplication.class);
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "invoice-watcher.event-processing.dao.mode", "jpa",
                    "invoice-watcher.event-processing.dao.jpa.data-source.basic.url",
                        "jdbc:h2:file:" + temporary.resolve("processing"),
                    "invoice-watcher.event-processing.dao.jpa.data-source.basic.username", "sa",
                    "invoice-watcher.event-processing.dao.jpa.data-source.basic.driver",
                        "org.h2.Driver",
                    "invoice-watcher.event-processing.dao.jpa.ddl-auto", "update",
                    "invoice-watcher.event-processing.dao.jpa.dialect",
                        "org.hibernate.dialect.H2Dialect")));
    context.refresh();
    return context.getBean(ProcessingDao.class);
  }

  private ProcessingDao restart() {
    context.close();
    return open();
  }

  @AfterEach
  void close() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName(
      "PROCESSOR-017 PROCESSOR-003 PROCESSOR-004: concurrent claims and fencing survive restart")
  void retainsAdmissionAndFences() throws Exception {
    // given
    var uut = open();
    // when
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          IntStream.range(0, 8)
              .mapToObj(index ->
                  executor.submit(
                      () -> uut.acquire("input", "stage", "v1", NOW, NOW.plusSeconds(10), 2)))
              .toList();
      int admitted = 0;
      for (var task : tasks) {
        if (task.get().decision() == ProcessingAdmissionDecision.ADMITTED) {
          admitted++;
        }
      }
      // then
      assertThat(admitted).isEqualTo(1);
    }
    var first = uut.find("input").orElseThrow();
    var recovered = restart();
    var next =
        recovered.acquire("input", "stage", "v1", NOW.plusSeconds(11), NOW.plusSeconds(30), 2);
    boolean stale = recovered.complete(first, NOW.plusSeconds(12), List.of());
    // then
    assertThat(next.entry().generation()).isEqualTo(first.generation() + 1);
    assertThat(next.entry().attempts()).isEqualTo(2);
    assertThat(stale).isFalse();
  }

  @Test
  @DisplayName(
      "PROCESSOR-017 PROCESSOR-005 PROCESSOR-006: failed outcome rolls back and outbox survives"
          + " confirmation loss")
  void commitsOutcomeAndOutboxTogether() {
    // given
    var uut = open();
    var claim = uut.acquire("input", "stage", "v1", NOW, NOW.plusSeconds(10), 2).entry();
    var oversized =
        ProcessingOutput.builder()
            .id("out")
            .topic("results")
            .valueBase64("x".repeat(900001))
            .build();
    // when
    var failure = catchThrowable(() -> uut.complete(claim, NOW.plusSeconds(1), List.of(oversized)));
    var unchanged = restart();
    // then
    assertThat(failure).isInstanceOf(IllegalArgumentException.class);
    assertThat(unchanged.find("input").orElseThrow().status()).isEqualTo(ProcessingStatus.RUNNING);
    assertThat(unchanged.pending("stage", 10)).isEmpty();
    // when
    var output =
        ProcessingOutput.builder()
            .id("out")
            .topic("results")
            .keyBase64("a2V5")
            .valueBase64("cGF5bG9hZA==")
            .build();
    unchanged.complete(claim, NOW.plusSeconds(2), List.of(output));
    var recovered = restart();
    var pending = recovered.pending("stage", 10);
    recovered.published("input", "out");
    recovered.published("input", "out");
    var confirmed = restart();
    // then
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().outputs()).containsExactly(output);
    assertThat(confirmed.pending("stage", 10)).isEmpty();
    assertThat(confirmed.find("input").orElseThrow().outputs().getFirst())
        .isEqualTo(output.confirmed());
  }

  @Test
  @DisplayName(
      "PROCESSOR-017 PROCESSOR-010 PROCESSOR-014: replay authorization and reopening grant one"
          + " budget")
  void retainsReplayAuthorizationAcrossRestart() throws Exception {
    // given
    var uut = open();
    var claim = uut.acquire("input", "stage", "v1", NOW, NOW.plusSeconds(10), 2).entry();
    var quarantine =
        uut.quarantine(
            "input", "stage", "v1", claim, NOW.plusSeconds(1), "failure", "reason", "location");
    uut.confirmQuarantine(quarantine, NOW.plusSeconds(2));
    var request =
        ReplayAudit.builder()
            .recordVersion(1)
            .id("request")
            .stage("stage")
            .processingVersion("v1")
            .topic("quarantine")
            .partition(0)
            .fromOffset(0)
            .toOffset(5)
            .limit(5)
            .rate(1)
            .operator("operator")
            .reason("repair")
            .createdAt(NOW)
            .nextOffset(0)
            .build();
    // when
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          IntStream.range(0, 4)
              .mapToObj(index -> executor.submit(() -> uut.authorize(request)))
              .toList();
      for (var task : tasks) {
        assertThat(task.get()).isEqualTo(request);
      }
    }
    var recovered = restart();
    boolean first = recovered.reopen("input", "request", NOW.plusSeconds(3));
    boolean duplicate = recovered.reopen("input", "request", NOW.plusSeconds(3));
    recovered.replayProgress("request", 4, false, null);
    var restarted = restart();
    // then
    assertThat(first).isTrue();
    assertThat(duplicate).isFalse();
    assertThat(restarted.find("input").orElseThrow().attempts()).isZero();
    assertThat(restarted.replay("request").orElseThrow().nextOffset()).isEqualTo(4);
    assertThat(restarted.authorize(request).nextOffset()).isEqualTo(4);
  }

  @Test
  @DisplayName("PROCESSOR-017 PROCESSOR-005: SQL write failure cannot accept a partial outcome")
  void rollsBackFailedDatabaseWrite() throws Exception {
    // given
    var uut = open();
    var claim = uut.acquire("input", "stage", "v1", NOW, NOW.plusSeconds(10), 2).entry();
    try (var connection = context.getBean(DataSource.class).getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "alter table event_processing_state add constraint short_snapshot"
              + " check (char_length(snapshot) < 2000)");
    }
    var output =
        ProcessingOutput.builder().id("out").topic("results").valueBase64("x".repeat(3000)).build();
    // when
    var failure = catchThrowable(() -> uut.complete(claim, NOW.plusSeconds(1), List.of(output)));
    var recovered = restart();
    // then
    assertThat(failure).hasMessage("Processing storage unavailable");
    assertThat(recovered.find("input").orElseThrow().status()).isEqualTo(ProcessingStatus.RUNNING);
    assertThat(recovered.pending("stage", 10)).isEmpty();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
