package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingEntry;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingStatus;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ReplayAudit;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.exception.ProcessingInfrastructureException;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.model.ProcessingAdmissionDecision;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.dao.eventprocessing.firestore.service.FirestoreProcessingDao;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.ProcessingFailure;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.TransportUnavailableException;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventContract;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventPublication;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingFailureReason;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingInput;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingOutcome;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingResultCandidate;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.model.RawEventRecord;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ReplayRequest;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ProcessorProperties;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ReplayProperties;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessingHandler;
import com.guslarz.invoicewatcher.eventprocessor.impl.configuration.ProcessorAutoConfiguration;
import com.guslarz.invoicewatcher.eventprocessor.kafka.configuration.ProcessorKafkaLifecycleAutoConfiguration;
import com.guslarz.invoicewatcher.eventprocessor.kafka.properties.ProcessorKafkaProperties;
import com.guslarz.invoicewatcher.eventprocessor.kafka.service.KafkaProcessorTransport;
import com.guslarz.invoicewatcher.eventproducer.api.EventEnvelope;
import com.guslarz.invoicewatcher.testing.testcontainers.SharedTestcontainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.gcloud.DatastoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class ProcessorIntegrationTest {
  @Container private static DatastoreEmulatorContainer emulator = SharedTestcontainers.datastore();

  @Container private static KafkaContainer kafka = SharedTestcontainers.kafka();
  private static final String SOURCE = "imap-v1:" + "a".repeat(64);
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final MutableClock clock = new MutableClock();
  private final AtomicInteger calls = new AtomicInteger();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private ExecutorService executor;
  private ProcessingDao dao;
  private KafkaProcessorTransport transport;
  private ProcessingStage stage;
  private ProcessorProperties properties;
  private DefaultProcessorRuntime uut;

  private ProcessorKafkaProperties kafkaProperties(String groupPrefix) {
    long retentionMs = Long.parseLong(ProcessorKafkaProperties.Defaults.RETENTION_MS);
    return ProcessorKafkaProperties.builder()
        .bootstrapServers(kafka.getBootstrapServers())
        .groupPrefix(groupPrefix)
        .offsetReset("earliest")
        .quarantineRetentionMs(retentionMs)
        .inputRetentionMs(retentionMs)
        .resultRetentionMs(retentionMs)
        .replayRetentionMs(retentionMs)
        .maxPublicationBytes(
            Integer.parseInt(ProcessorKafkaProperties.Defaults.MAX_PUBLICATION_BYTES))
        .concurrency(Integer.parseInt(ProcessorKafkaProperties.Defaults.CONCURRENCY))
        .build();
  }

  @BeforeEach
  void prepare() throws Exception {
    String name = "s" + UUID.randomUUID().toString().replace("-", "");
    dao = spy(processingDao(name));
    executor = Executors.newVirtualThreadPerTaskExecutor();
    properties =
        new ProcessorProperties(
            2,
            List.of(Duration.ofSeconds(1)),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofMillis(100),
            100,
            524288);
    stage =
        stage(
            name,
            "v1",
            input -> {
              calls.incrementAndGet();
              return candidates(ProcessingOutcome.COMPLETED);
            });
    try (Admin admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(stage.inputTopic(), 1, (short) 1)
                      .configs(
                          Map.of(
                              "cleanup.policy",
                              "delete",
                              "retention.ms",
                              "604800000",
                              "retention.bytes",
                              "-1")),
                  new NewTopic(stage.resultTopic(), 1, (short) 1)
                      .configs(
                          Map.of(
                              "cleanup.policy",
                              "delete",
                              "retention.ms",
                              "604800000",
                              "retention.bytes",
                              "-1")),
                  new NewTopic(stage.replayTopic(), 1, (short) 1)
                      .configs(
                          Map.of(
                              "max.message.bytes",
                              "2097152",
                              "cleanup.policy",
                              "delete",
                              "retention.ms",
                              "604800000",
                              "retention.bytes",
                              "-1")),
                  new NewTopic(stage.quarantineTopic(), 1, (short) 1)
                      .configs(
                          Map.of(
                              "cleanup.policy",
                              "delete",
                              "retention.ms",
                              "604800000",
                              "retention.bytes",
                              "-1",
                              "max.message.bytes",
                              "4194304"))))
          .all()
          .get(20, TimeUnit.SECONDS);
    }
    transport = spy(new KafkaProcessorTransport(kafkaProperties("test-"), Map.of()));
    runtime();
  }

  protected ProcessingDao processingDao(String name) {
    Datastore datastore =
        DatastoreOptions.newBuilder()
            .setProjectId(emulator.getProjectId())
            .setHost("http://" + emulator.getEmulatorEndpoint())
            .setNamespace(name)
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();
    return new FirestoreProcessingDao(datastore);
  }

  protected void closePersistence() {}

  @AfterEach
  void close() {
    transport.close();
    executor.shutdownNow();
    registry.close();
    closePersistence();
  }

  @Test
  @DisplayName("PROCESSOR-016: input retention drift fails preflight before consumption")
  void rejectsInputRetentionDrift() throws Exception {
    // given
    var resource = new ConfigResource(ConfigResource.Type.TOPIC, stage.inputTopic());
    try (Admin admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      admin
          .incrementalAlterConfigs(
              Map.of(
                  resource,
                  List.of(
                      new AlterConfigOp(
                          new ConfigEntry("retention.ms", "3600000"), AlterConfigOp.OpType.SET))))
          .all()
          .get(15, TimeUnit.SECONDS);
    }
    // when
    var failure = catchThrowable(() -> transport.preflight(stage));
    // then
    assertThat(failure)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsuitable application topic retention settings");
  }

  @Test
  @DisplayName(
      "PROCESSOR-003 PROCESSOR-004 PROCESSOR-006: duplicate delivery and restart retain immutable"
          + " results")
  void recoversCommittedOutboxWithoutRerunningHandler() {
    // given
    var raw = input(0);
    // when
    assertThat(uut.process(stage, raw).acknowledge()).isTrue();
    runtime();
    assertThat(uut.process(stage, input(5)).acknowledge()).isTrue();
    uut.recover(stage);
    var published = transport.read(stage.resultTopic(), 0, 0);
    var result = mapper.readTree(published.valueBytes());
    // then
    assertThat(calls).hasValue(1);
    assertThat(result.path("eventType").asString()).isEqualTo("fixture.recognized");
    assertThat(result.path("payload").path("sourceRefs").get(0).path("offset").asLong()).isZero();
    assertThat(result.path("payload").path("sourceRefs").get(0).path("sourceId").asString())
        .isEqualTo(SOURCE);
    assertThat(new String(published.keyBytes(), StandardCharsets.UTF_8))
        .isEqualTo("tenant@example.com");
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .satisfies(entry -> {
          assertThat(entry.status()).isEqualTo(ProcessingStatus.COMPLETE);
          assertThat(entry.outputs())
              .singleElement()
              .satisfies(output -> {
                assertThat(output.published()).isTrue();
                assertThat(output.valueBase64()).isNull();
              });
        });
  }

  @Test
  @DisplayName(
      "PROCESSOR-006 PROCESSOR-014: uncertain broker confirmation repeats stable bytes and clears"
          + " stored body")
  void republishesAfterPublicationConfirmationIsLost() {
    // given
    uut.process(stage, input(0));
    doThrow(new ProcessingInfrastructureException()).when(dao).published(anyString(), anyString());
    // when
    uut.recover(stage);
    final var first = transport.read(stage.resultTopic(), 0, 0);
    doAnswer(
            call -> {
              call.callRealMethod();
              return null;
            })
        .when(dao)
        .published(anyString(), anyString());
    runtime();
    uut.recover(stage);
    var second = transport.read(stage.resultTopic(), 0, 1);
    // then
    assertThat(second.valueBase64()).isEqualTo(first.valueBase64());
    assertThat(calls).hasValue(1);
    assertThat(dao.pending(stage.name(), 10)).isEmpty();
  }

  @Test
  @DisplayName("PROCESSOR-005: expired claims fence stale completions and concurrent admission")
  void fencesExpiredClaims() {
    // given
    Instant now = clock.instant();
    final var first = dao.acquire("claim", stage.name(), "v1", now, now.plusSeconds(2), 4);
    // when
    var concurrent = dao.acquire("claim", stage.name(), "v1", now, now.plusSeconds(2), 4);
    clock.advance(Duration.ofSeconds(3));
    var replacement =
        dao.acquire(
            "claim", stage.name(), "v1", clock.instant(), clock.instant().plusSeconds(2), 4);
    // then
    assertThat(concurrent.decision()).isEqualTo(ProcessingAdmissionDecision.WAIT);
    assertThat(dao.complete(first.entry(), clock.instant(), List.of())).isFalse();
    assertThat(dao.complete(replacement.entry(), clock.instant(), List.of())).isTrue();
    assertThat(dao.find("claim").orElseThrow().attempts()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "PROCESSOR-007 PROCESSOR-008 PROCESSOR-010: retry budget survives restart and quarantine"
          + " confirmation failure")
  void boundsRetryAndWaitsForQuarantineConfirmation() {
    // given
    handler(input -> {
      calls.incrementAndGet();
      throw new ProcessingFailure(ProcessingFailureReason.HANDLER_FAILURE, true);
    });
    // when
    final var first = uut.process(stage, input(0));
    runtime();
    final var early = uut.process(stage, input(0));
    clock.advance(Duration.ofSeconds(2));
    doThrow(new TransportUnavailableException()).when(transport).publish(any());
    final var unavailable = uut.process(stage, input(0));
    doAnswer(
            call -> {
              call.callRealMethod();
              return null;
            })
        .when(transport)
        .publish(any());
    runtime();
    var confirmed = uut.process(stage, input(0));
    var quarantine =
        mapper.readTree(transport.read(stage.quarantineTopic(), 0, 0).valueBytes()).path("payload");
    // then
    assertThat(first.acknowledge()).isFalse();
    assertThat(early.acknowledge()).isFalse();
    assertThat(unavailable.acknowledge()).isFalse();
    assertThat(confirmed.acknowledge()).isTrue();
    assertThat(calls).hasValue(2);
    assertThat(quarantine.path("attempts").asInt()).isEqualTo(2);
    assertThat(quarantine.path("reason").asString()).isEqualTo("RETRY_EXHAUSTED");
    assertThat(quarantine.path("original").path("valueBase64").asString())
        .isEqualTo(input(0).valueBase64());
  }

  @Test
  @DisplayName(
      "PROCESSOR-002 PROCESSOR-010 PROCESSOR-013: invalid and legacy records preserve bytes without"
          + " fabricated identity or headers")
  void quarantinesInvalidBytesWithoutLeakingDiagnostics() {
    // given
    List<RawEventRecord> invalid =
        List.of(
            RawEventRecord.builder()
                .topic(stage.inputTopic())
                .partition(0)
                .offset(0)
                .timestamp(1)
                .keyBase64(null)
                .valueBase64(null)
                .headers(Map.of("authorization", "secret-token"))
                .build(),
            RawEventRecord.builder()
                .topic(stage.inputTopic())
                .partition(0)
                .offset(1)
                .timestamp(1)
                .keyBase64(ProcessingModelSupport.encodeText("tenant"))
                .valueBase64(ProcessingModelSupport.encode(new byte[] {(byte) 255, 0}))
                .headers(Map.of())
                .build(),
            RawEventRecord.builder()
                .topic(stage.inputTopic())
                .partition(0)
                .offset(2)
                .timestamp(1)
                .keyBase64(ProcessingModelSupport.encodeText("tenant"))
                .valueBase64(ProcessingModelSupport.encodeText("{\"title\":\"secret-title\"}"))
                .headers(Map.of())
                .build());
    // when
    for (RawEventRecord record : invalid) {
      assertThat(uut.process(stage, record).acknowledge()).isTrue();
    }
    // then
    assertThat(calls).hasValue(0);
    for (int index = 0; index < invalid.size(); index++) {
      var payload =
          mapper
              .readTree(transport.read(stage.quarantineTopic(), 0, index).valueBytes())
              .path("payload");
      assertThat(payload.path("sourceId").isNull()).isTrue();
      assertThat(payload.path("original").path("headers").isEmpty()).isTrue();
      assertThat(
              payload.path("original").path("valueBase64").isNull()
                  ? null
                  : payload.path("original").path("valueBase64").asString())
          .isEqualTo(invalid.get(index).valueBase64());
    }
  }

  @Test
  @DisplayName(
      "PROCESSOR-009: shared infrastructure pauses work without consuming handler attempts")
  void refundsAdmissionWhenSharedDependencyIsUnavailable() {
    // given
    handler(input -> {
      calls.incrementAndGet();
      throw new ProcessingInfrastructureException();
    });
    // when
    var result = uut.process(stage, input(0));
    // then
    assertThat(result.acknowledge()).isFalse();
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .satisfies(entry -> {
          assertThat(entry.attempts()).isZero();
          assertThat(entry.status()).isEqualTo(ProcessingStatus.RETRY);
        });
    assertThat(registry.get("invoice.processor.paused").gauge().value()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "PROCESSOR-004 PROCESSOR-012 PROCESSOR-013: review is completed, measured with bounded labels"
          + " and safe diagnostics")
  void keepsReviewOutOfTechnicalQuarantine() {
    // given
    handler(
        new ProcessingHandler() {
          @Override
          public Set<String> reasonCodes() {
            return Set.of("LOW_CONFIDENCE");
          }

          @Override
          public List<ProcessingResultCandidate> handle(ProcessingInput input) {
            return List.of(
                ProcessingResultCandidate.builder()
                    .localId("item")
                    .contract(new EventContract("fixture.recognized", 1))
                    .method("fixture")
                    .outcome(ProcessingOutcome.REVIEW_REQUIRED)
                    .reasonCodes(List.of("LOW_CONFIDENCE"))
                    .evidence(mapper.createObjectNode().put("secret", "financial-data"))
                    .build());
          }
        });
    // when
    uut.process(stage, input(0));
    uut.recover(stage);
    // then
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .extracting(ProcessingEntry::status)
        .isEqualTo(ProcessingStatus.COMPLETE);
    assertThat(registry.get("invoice.processor.review").counter().count()).isEqualTo(1);
    assertThat(registry.get("invoice.processor.last.success").gauge().value())
        .isEqualTo(clock.millis() / 1000.0);
    registry
        .getMeters()
        .forEach(meter ->
            assertThat(meter.getId().getTags().toString())
                .doesNotContain(SOURCE, "tenant@example.com", "financial-data"));
  }

  @Test
  @DisplayName(
      "PROCESSOR-011: bounded replay is audited, resumable and cannot repeatedly reopen a failure")
  void replaysQuarantineThroughDurableAuthorization() {
    // given
    handler(input -> {
      throw new ProcessingFailure(ProcessingFailureReason.PERMANENT_FAILURE, false);
    });
    uut.process(stage, input(0));
    var request =
        ReplayRequest.builder()
            .requestId("request-1")
            .stage(stage.name())
            .processingVersion("v1")
            .partition(0)
            .fromOffset(0)
            .toOffset(1)
            .limit(1)
            .rate(1)
            .operator("operator")
            .reason("RETRY_FIXED_DEPENDENCY")
            .build();
    var replay =
        new DefaultReplayService(
            dao, transport, List.of(stage), new ReplayProperties(100, 1), clock);
    // when
    final var preview = replay.preview(request);
    assertThat(dao.replay("request-1")).isEmpty();
    replay.execute(request);
    replay.execute(request);
    var delivery = transport.read(stage.replayTopic(), 0, 0);
    handler(input -> {
      calls.incrementAndGet();
      return candidates(ProcessingOutcome.COMPLETED);
    });
    assertThat(uut.process(stage, delivery).acknowledge()).isTrue();
    assertThat(uut.process(stage, delivery).acknowledge()).isTrue();
    // then
    assertThat(preview.count()).isEqualTo(1);
    assertThat(calls).hasValue(1);
    assertThat(dao.replay("request-1").orElseThrow().finished()).isTrue();
    assertThat(delivery.valueBase64()).isEqualTo(input(0).valueBase64());
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .extracting(ProcessingEntry::status)
        .isEqualTo(ProcessingStatus.COMPLETE);
  }

  @Test
  @DisplayName(
      "PROCESSOR-011 PROCESSOR-014: expired evidence is reported and preview performs no writes")
  void rejectsExpiredReplayEvidence() throws Exception {
    // given
    handler(input -> {
      throw new ProcessingFailure(ProcessingFailureReason.PERMANENT_FAILURE, false);
    });
    uut.process(stage, input(0));
    var replay =
        new DefaultReplayService(
            dao, transport, List.of(stage), new ReplayProperties(100, 1), clock);
    try (Admin admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      admin
          .deleteRecords(
              Map.of(
                  new TopicPartition(stage.quarantineTopic(), 0), RecordsToDelete.beforeOffset(1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }
    var request =
        ReplayRequest.builder()
            .requestId("expired")
            .stage(stage.name())
            .processingVersion("v1")
            .partition(0)
            .fromOffset(0)
            .toOffset(1)
            .limit(1)
            .rate(1)
            .operator("operator")
            .reason("RECOVER")
            .build();
    // when
    Throwable failure = catchThrowable(() -> replay.preview(request));
    // then
    assertThat(failure).isInstanceOf(ProcessingFailure.class).hasMessage("EVIDENCE_EXPIRED");
    assertThat(dao.replay("expired")).isEmpty();
  }

  @Test
  @DisplayName("PROCESSOR-015: oversized results quarantine without a partial successful outcome")
  void rejectsOversizedResultsBeforeCompletion() {
    // given
    handler(input ->
        List.of(
            ProcessingResultCandidate.builder()
                .localId("huge")
                .contract(new EventContract("fixture.recognized", 1))
                .method("fixture")
                .outcome(ProcessingOutcome.COMPLETED)
                .reasonCodes(List.of())
                .evidence(mapper.createObjectNode().put("content", "x".repeat(524288)))
                .build()));
    // when
    var result = uut.process(stage, input(0));
    // then
    assertThat(result.acknowledge()).isTrue();
    assertThat(dao.pending(stage.name(), 10)).isEmpty();
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .satisfies(entry -> {
          assertThat(entry.status()).isEqualTo(ProcessingStatus.QUARANTINED);
          assertThat(entry.reason()).isEqualTo("RESULT_TOO_LARGE");
        });
  }

  @Test
  @DisplayName(
      "PROCESSOR-001 PROCESSOR-014 PROCESSOR-015: preflight checks declared topics, retention and"
          + " size")
  void checksTopicConfigurationBeforeConsuming() {
    // given
    // when
    transport.preflight(stage);
    // then
    assertThat(transport.lag(stage.inputTopic(), "missing-group")).containsEntry(0, -1L);
  }

  @Test
  @DisplayName(
      "PROCESSOR-001 PROCESSOR-007 PROCESSOR-008: actual listener retries safely and commits"
          + " contiguous offsets")
  void runsRegisteredKafkaConsumerAndCommitsAfterRecovery() {
    // given
    handler(input -> {
      if (calls.incrementAndGet() == 1) {
        throw new ProcessingFailure(ProcessingFailureReason.HANDLER_FAILURE, true);
      }
      return candidates(ProcessingOutcome.COMPLETED);
    });
    var runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ProcessorAutoConfiguration.class,
                    ProcessorKafkaLifecycleAutoConfiguration.class))
            .withBean(ProcessingStage.class, () -> stage)
            .withBean(ProcessingDao.class, () -> dao)
            .withBean(KafkaProcessorTransport.class, () -> transport)
            .withBean(ProcessorKafkaProperties.class, () -> kafkaProperties("live-"))
            .withPropertyValues(
                "invoice-watcher.event-processing.dao.mode=firestore",
                "invoice-watcher.event-processor.retry-delays=100ms",
                "invoice-watcher.event-processor.outbox-interval=100ms");
    // when
    runner.run(context -> {
      assertThat(context).hasNotFailed();
      var raw = input(0);
      transport.publish(
          EventPublication.builder()
              .id("source")
              .topic(stage.inputTopic())
              .keyBase64(raw.keyBase64())
              .valueBase64(raw.valueBase64())
              .headers(Map.of())
              .build());
      Awaitility.await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(() -> {
            // then
            assertThat(calls).hasValue(2);
            assertThat(transport.lag(stage.inputTopic(), "live-" + stage.name()))
                .containsEntry(0, 0L);
            assertThat(dao.pending(stage.name(), 10)).isEmpty();
            assertThat(transport.read(stage.resultTopic(), 0, 0).valueBase64()).isNotBlank();
          });
    });
  }

  @Test
  @DisplayName(
      "PROCESSOR-008: handler timeout consumes one durable attempt and late work cannot complete")
  void timesOutHandlerWithoutAcceptingLateResult() {
    // given
    properties =
        new ProcessorProperties(
            1,
            List.of(Duration.ofMillis(1)),
            Duration.ofMillis(20),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofMillis(100),
            100,
            524288);
    handler(input -> {
      try {
        new CountDownLatch(1).await(1, TimeUnit.SECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      return candidates(ProcessingOutcome.COMPLETED);
    });
    runtime();
    // when
    var outcome = uut.process(stage, input(0));
    // then
    assertThat(outcome.acknowledge()).isTrue();
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .satisfies(entry -> {
          assertThat(entry.status()).isEqualTo(ProcessingStatus.QUARANTINED);
          assertThat(entry.attempts()).isEqualTo(1);
        });
    assertThat(dao.pending(stage.name(), 10)).isEmpty();
  }

  @Test
  @DisplayName(
      "PROCESSOR-002: unsupported contracts and invalid required fields never call the handler")
  void rejectsUnsupportedOrInvalidContracts() {
    // given
    List<String> values =
        List.of(
            "{\"eventType\":\"mail.ingested\",\"schemaVersion\":2,\"payload\":{}}",
            "{\"eventType\":\"other.event\",\"schemaVersion\":1,\"payload\":{}}",
            "{\"eventType\":\"mail.ingested\",\"schemaVersion\":1.5,\"payload\":{}}",
            "{\"eventType\":\"mail.ingested\",\"schemaVersion\":1,\"payload\":"
                + "{\"sourceId\":\"untrusted-private-value\"}}");
    // when
    for (int index = 0; index < values.size(); index++) {
      var raw =
          RawEventRecord.builder()
              .topic(stage.inputTopic())
              .partition(0)
              .offset(index)
              .timestamp(1)
              .keyBase64(ProcessingModelSupport.encodeText("tenant"))
              .valueBase64(ProcessingModelSupport.encodeText(values.get(index)))
              .headers(Map.of())
              .build();
      assertThat(uut.process(stage, raw).acknowledge()).isTrue();
    }
    // then
    assertThat(calls).hasValue(0);
    assertThat(dao.entries(stage.name()))
        .allMatch(entry -> entry.status() == ProcessingStatus.QUARANTINED);
  }

  @Test
  @DisplayName("PROCESSOR-011: forged replay headers cannot grant a fresh retry budget")
  void rejectsReplayWithoutDurableAuthorization() {
    // given
    var original = input(0);
    var forged =
        RawEventRecord.builder()
            .topic(stage.replayTopic())
            .partition(0)
            .offset(0)
            .timestamp(1)
            .keyBase64(original.keyBase64())
            .valueBase64(original.valueBase64())
            .headers(
                Map.of(
                    "iw-replay-request",
                    "forged",
                    "iw-replay-offset",
                    "0",
                    "iw-replay-partition",
                    "0"))
            .build();
    // when
    var outcome = uut.process(stage, forged);
    // then
    assertThat(outcome.acknowledge()).isTrue();
    assertThat(calls).hasValue(0);
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .extracting(ProcessingEntry::reason)
        .isEqualTo("INVALID_REPLAY");
  }

  @Test
  @DisplayName(
      "PROCESSOR-015 PROCESSOR-007: oversized quarantine pauses without acknowledging the input")
  void refusesToAcknowledgeOversizedQuarantine() {
    // given
    var huge =
        RawEventRecord.builder()
            .topic(stage.inputTopic())
            .partition(0)
            .offset(0)
            .timestamp(1)
            .keyBase64(ProcessingModelSupport.encodeText("tenant"))
            .valueBase64(ProcessingModelSupport.encodeText("x".repeat(3_200_000)))
            .headers(Map.of())
            .build();
    // when
    var outcome = uut.process(stage, huge);
    // then
    assertThat(outcome.acknowledge()).isFalse();
    assertThat(dao.entries(stage.name()))
        .singleElement()
        .extracting(ProcessingEntry::status)
        .isEqualTo(ProcessingStatus.QUARANTINE_PENDING);
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  @DisplayName(
      "PROCESSOR-013: runtime logs do not include input content, tenant keys or exception causes")
  void redactsHandlerExceptionDiagnostics(CapturedOutput output) {
    // given
    handler(input -> {
      throw new IllegalStateException(
          "secret-exception-body", new RuntimeException("secret-cause"));
    });
    // when
    uut.process(stage, input(0));
    clock.advance(Duration.ofSeconds(2));
    uut.process(stage, input(0));
    // then
    assertThat(output.getAll())
        .doesNotContain(
            "secret-exception-body",
            "secret-cause",
            "private-body",
            "private-title",
            "sender@example.com",
            "tenant@example.com");
    assertThat(output.getAll()).contains("processingId=", "reason=");
  }

  @Test
  @DisplayName("PROCESSOR-011: stale replay confirmations cannot erase a terminal audit failure")
  void retainsReplayFailureWhenAnOlderSenderConfirms() {
    // given
    var request =
        ReplayAudit.builder()
            .recordVersion(1)
            .id("partial")
            .stage(stage.name())
            .processingVersion("v1")
            .topic(stage.quarantineTopic())
            .partition(0)
            .fromOffset(0)
            .toOffset(2)
            .limit(2)
            .rate(1)
            .operator("operator")
            .reason("RECOVER")
            .createdAt(clock.instant())
            .nextOffset(0)
            .finished(false)
            .failure(null)
            .build();
    dao.authorize(request);
    // when
    dao.replayProgress("partial", 1, true, "EVIDENCE_EXPIRED");
    dao.replayProgress("partial", 1, false, null);
    // then
    var audit = dao.replay("partial").orElseThrow();
    assertThat(audit.failure()).isEqualTo("EVIDENCE_EXPIRED");
    assertThat(audit.finished()).isTrue();
    assertThat(audit.nextOffset()).isEqualTo(1);
  }

  private void runtime() {
    uut =
        new DefaultProcessorRuntime(
            dao, transport, properties, clock, executor, new ProcessorMetrics(registry, clock));
  }

  private ProcessingStage stage(String name, String version, ProcessingHandler handler) {
    return ProcessingStage.builder()
        .name(name)
        .processingVersion(version)
        .inputTopic(name + ".input")
        .resultTopic(name + ".results")
        .quarantineTopic(name + ".quarantine")
        .replayTopic(name + ".replay")
        .inputs(Set.of(new EventContract("mail.ingested", 1)))
        .outputs(Set.of(new EventContract("fixture.recognized", 1)))
        .handler(handler)
        .build();
  }

  private void handler(ProcessingHandler handler) {
    stage = stage(stage.name(), stage.processingVersion(), handler);
  }

  private List<ProcessingResultCandidate> candidates(ProcessingOutcome outcome) {
    return List.of(
        ProcessingResultCandidate.builder()
            .localId("item")
            .contract(new EventContract("fixture.recognized", 1))
            .method("fixture")
            .outcome(outcome)
            .reasonCodes(List.of())
            .evidence(mapper.createObjectNode().put("sample", true))
            .build());
  }

  private RawEventRecord input(long offset) {
    var payload =
        mapper
            .createObjectNode()
            .put("sourceId", SOURCE)
            .put("content", "private-body")
            .put("contentType", "text/plain")
            .put("date", "2026-11-16T12:00:00Z")
            .put("title", "private-title")
            .put("sender", "sender@example.com");
    var envelope = new EventEnvelope<>("mail.ingested", 1, payload);
    return RawEventRecord.builder()
        .topic(stage.inputTopic())
        .partition(0)
        .offset(offset)
        .timestamp(1)
        .keyBase64(ProcessingModelSupport.encodeText("tenant@example.com"))
        .valueBase64(ProcessingModelSupport.encode(mapper.writeValueAsBytes(envelope)))
        .headers(Map.of())
        .build();
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-16T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
