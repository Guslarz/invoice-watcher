package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingEntry;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingOutput;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ProcessingStatus;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.exception.ProcessingInfrastructureException;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.ProcessingFailure;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.PublicationTooLargeException;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.TransportUnavailableException;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventContract;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventPublication;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingDisposition;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingFailureReason;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingInput;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingOutcome;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingResultCandidate;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingResultPayload;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.model.QuarantinedRecord;
import com.guslarz.invoicewatcher.eventprocessor.api.model.RawEventRecord;
import com.guslarz.invoicewatcher.eventprocessor.api.model.SourceRecordReference;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ProcessorProperties;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorRuntime;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorTransport;
import com.guslarz.invoicewatcher.eventproducer.api.EventEnvelope;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Durable orchestration independent of broker and database implementation classes. */
@Slf4j
@Component
@RequiredArgsConstructor
public final class DefaultProcessorRuntime implements ProcessorRuntime {
  private final ProcessingDao dao;
  private final ProcessorTransport transport;
  private final ProcessorProperties properties;

  @Qualifier("processorClock")
  private final Clock clock;

  @Qualifier("processorExecutor")
  private final ExecutorService executor;

  private static final Pattern MAIL_SOURCE = Pattern.compile("imap-v1:[0-9a-f]{64}");
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final ProcessorMetrics metrics;

  @Override
  public ProcessingDisposition process(ProcessingStage stage, RawEventRecord delivered) {
    metrics.count(stage, "received");
    try {
      ProcessingDisposition result = processRecord(stage, delivered);
      metrics.gauge(stage, "paused", result.acknowledge() ? 0 : 1);
      return result;
    } catch (PublicationTooLargeException failure) {
      metrics.gauge(stage, "paused", 1);
      log.warn(
          "Processor stage={} reason=QUARANTINE_TOO_LARGE bytes={} limit={}",
          stage.name(),
          failure.actualBytes(),
          failure.limitBytes());
      return waitUntil(clock.instant().plus(properties.recoveryInterval()));
    } catch (ProcessingInfrastructureException | TransportUnavailableException failure) {
      metrics.gauge(stage, "paused", 1);
      log.warn("Processor stage={} reason=INFRASTRUCTURE_UNAVAILABLE", stage.name());
      return waitUntil(clock.instant().plus(properties.recoveryInterval()));
    }
  }

  private ProcessingDisposition processRecord(ProcessingStage stage, RawEventRecord delivered) {
    RawEventRecord raw = delivered;
    if (raw.topic().equals(stage.replayTopic())) {
      try {
        raw = DefaultReplayService.original(stage, raw, dao, transport, mapper);
      } catch (ProcessingFailure failure) {
        return invalid(stage, delivered, failure.reason());
      }
    }
    Decoded decoded;
    try {
      decoded = decode(stage, raw);
    } catch (ProcessingFailure failure) {
      return invalid(stage, raw, failure.reason());
    }
    if (delivered.topic().equals(stage.replayTopic())) {
      String request = delivered.headers().get("iw-replay-request");
      boolean opened = dao.reopen(decoded.id(), request, clock.instant());
      ProcessingEntry current = dao.find(decoded.id()).orElse(null);
      if (!opened
          && (current == null
              || !request.equals(current.replayRequestId())
              || current.status() == ProcessingStatus.QUARANTINED)) {
        if (current != null
            && (current.status() == ProcessingStatus.COMPLETE
                || (request.equals(current.replayRequestId())
                    && current.status() == ProcessingStatus.QUARANTINED))) {
          metrics.count(stage, "duplicate");
          return acknowledged();
        }
        return invalid(stage, delivered, ProcessingFailureReason.INVALID_REPLAY);
      }
    }
    Instant now = clock.instant();
    var admission =
        dao.acquire(
            decoded.id(),
            stage.name(),
            stage.processingVersion(),
            now,
            now.plus(properties.lease()),
            properties.maxAttempts());
    ProcessingEntry claim = admission.entry();
    return switch (admission.decision()) {
      case COMPLETE -> {
        metrics.count(stage, "duplicate");
        yield acknowledged();
      }
      case WAIT ->
          waitUntil(
              claim.status() == ProcessingStatus.RUNNING ? claim.leaseUntil() : claim.dueAt());
      case QUARANTINE ->
          quarantine(
              stage,
              raw,
              decoded,
              claim,
              claim.reason() == null
                  ? ProcessingFailureReason.RETRY_EXHAUSTED
                  : ProcessingFailureReason.valueOf(claim.reason()));
      case ADMITTED -> handle(stage, raw, decoded, claim);
    };
  }

  private ProcessingDisposition handle(
      ProcessingStage stage, RawEventRecord raw, Decoded decoded, ProcessingEntry claim) {
    if (claim.attempts() > 1) {
      metrics.count(stage, "retry");
    }
    var input =
        ProcessingInput.builder()
            .tenant(decoded.tenant())
            .processingId(decoded.id())
            .source(decoded.source())
            .payload(decoded.payload())
            .startedAt(claim.startedAt())
            .deadline(clock.instant().plus(properties.handlerTimeout()))
            .build();
    long started = System.nanoTime();
    var future =
        executor.submit(
            () -> {
              stage.handler().validate(decoded.payload());
              return stage.handler().handle(input);
            });
    try {
      List<ProcessingResultCandidate> candidates =
          future.get(properties.handlerTimeout().toMillis(), TimeUnit.MILLISECONDS);
      List<ProcessingOutput> outputs = outputs(stage, decoded, claim, candidates);
      if (!dao.complete(claim, clock.instant(), outputs)) {
        return waitUntil(claim.leaseUntil());
      }
      metrics.count(stage, "completed");
      candidates.forEach(
          candidate -> candidate.reasonCodes().forEach(reason -> metrics.review(stage, reason)));
      if (candidates.stream().anyMatch(c -> c.outcome() == ProcessingOutcome.REVIEW_REQUIRED)) {
        metrics.count(stage, "review");
      }
      log.info(
          "Processor stage={} processingId={} sourceId={} attempt={} outcome=COMPLETE",
          stage.name(),
          decoded.id(),
          decoded.source().sourceId(),
          claim.attempts());
      return acknowledged();
    } catch (TimeoutException failure) {
      future.cancel(true);
      return failed(
          stage,
          raw,
          decoded,
          claim,
          new ProcessingFailure(ProcessingFailureReason.HANDLER_TIMEOUT, true));
    } catch (InterruptedException failure) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      return waitUntil(claim.leaseUntil());
    } catch (ExecutionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof ProcessingInfrastructureException
          || cause instanceof TransportUnavailableException) {
        Instant due = clock.instant().plus(properties.recoveryInterval());
        dao.defer(claim, clock.instant(), due);
        return waitUntil(due);
      }
      ProcessingFailure classified =
          cause instanceof ProcessingFailure recordFailure
              ? recordFailure
              : new ProcessingFailure(ProcessingFailureReason.HANDLER_FAILURE, true);
      return failed(stage, raw, decoded, claim, classified);
    } catch (ProcessingFailure failure) {
      return failed(stage, raw, decoded, claim, failure);
    } finally {
      metrics.duration(stage, System.nanoTime() - started);
    }
  }

  private ProcessingDisposition failed(
      ProcessingStage stage,
      RawEventRecord raw,
      Decoded decoded,
      ProcessingEntry claim,
      ProcessingFailure failure) {
    if (!failure.retryable() || claim.attempts() >= properties.maxAttempts()) {
      return quarantine(
          stage,
          raw,
          decoded,
          claim,
          failure.retryable() ? ProcessingFailureReason.RETRY_EXHAUSTED : failure.reason());
    }
    int index = Math.min(claim.attempts() - 1, properties.retryDelays().size() - 1);
    long base = properties.retryDelays().get(index).toMillis();
    long jittered = Math.max(1, (long) (base * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
    Instant due = clock.instant().plusMillis(jittered);
    dao.retry(claim, clock.instant(), due, failure.reason().name());
    log.info(
        "Processor stage={} processingId={} attempt={} reason={}",
        stage.name(),
        decoded.id(),
        claim.attempts(),
        failure.reason());
    return waitUntil(due);
  }

  private List<ProcessingOutput> outputs(
      ProcessingStage stage,
      Decoded input,
      ProcessingEntry claim,
      List<ProcessingResultCandidate> candidates) {
    try {
      if (candidates == null) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_RESULT, false);
      }
      List<ProcessingOutput> outputs = new ArrayList<>();
      var ids = new HashSet<String>();
      long total = 0;
      for (ProcessingResultCandidate candidate : candidates) {
        if (candidate == null
            || !ids.add(candidate.localId())
            || !stage.outputs().contains(candidate.contract())
            || !stage.handler().reasonCodes().containsAll(candidate.reasonCodes())) {
          throw new ProcessingFailure(ProcessingFailureReason.INVALID_RESULT, false);
        }
        String id = ProcessingIdentities.hash("result-v1", input.id(), candidate.localId());
        var payload =
            ProcessingResultPayload.builder()
                .resultId(id)
                .processingId(input.id())
                .sourceRefs(List.of(input.source()))
                .method(candidate.method())
                .processingVersion(stage.processingVersion())
                .startedAt(claim.startedAt())
                .completedAt(clock.instant())
                .outcome(candidate.outcome())
                .reasonCodes(candidate.reasonCodes())
                .evidence(candidate.evidence())
                .build();
        var envelope =
            new EventEnvelope<>(
                candidate.contract().eventType(), candidate.contract().schemaVersion(), payload);
        byte[] bytes = mapper.writeValueAsBytes(envelope);
        total += bytes.length;
        if (total > properties.maxResultBytes()) {
          throw new ProcessingFailure(ProcessingFailureReason.RESULT_TOO_LARGE, false);
        }
        outputs.add(
            ProcessingOutput.builder()
                .id(id)
                .topic(stage.resultTopic())
                .keyBase64(ProcessingModelSupport.encodeText(input.tenant()))
                .valueBase64(ProcessingModelSupport.encode(bytes))
                .published(false)
                .build());
      }
      if (mapper.writeValueAsBytes(outputs).length + 4096L > dao.maxStoredOutcomeBytes()) {
        throw new ProcessingFailure(ProcessingFailureReason.RESULT_TOO_LARGE, false);
      }
      return List.copyOf(outputs);
    } catch (ProcessingFailure failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RESULT, false);
    }
  }

  private Decoded decode(ProcessingStage stage, RawEventRecord raw) {
    String tenant;
    try {
      if (raw.keyBytes() == null) {
        throw new CharacterCodingException();
      }
      tenant =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(raw.keyBytes()))
              .toString();
      if (tenant.isBlank()) {
        throw new CharacterCodingException();
      }
    } catch (CharacterCodingException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_TENANT, false);
    }
    JsonNode envelope;
    try {
      if (raw.valueBytes() == null) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
      }
      envelope = mapper.readTree(raw.valueBytes());
    } catch (RuntimeException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
    }
    if (envelope == null || !envelope.isObject()) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
    }
    if (!envelope.has("eventType") && !envelope.has("schemaVersion")) {
      throw new ProcessingFailure(ProcessingFailureReason.LEGACY_RECORD, false);
    }
    var type = envelope.path("eventType");
    var version = envelope.path("schemaVersion");
    if (!type.isString() || !version.isIntegralNumber() || !version.canConvertToInt()) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
    }
    EventContract contract;
    try {
      contract = new EventContract(type.asString(), version.asInt());
    } catch (IllegalArgumentException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
    }
    if (!stage.inputs().contains(contract)) {
      throw new ProcessingFailure(ProcessingFailureReason.UNSUPPORTED_CONTRACT, false);
    }
    JsonNode payload = envelope.path("payload");
    JsonNode source = payload.path("sourceId");
    if (!payload.isObject()
        || !source.isString()
        || !MAIL_SOURCE.matcher(source.asString()).matches()) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_SOURCE, false);
    }
    if (contract.eventType().equals("mail.ingested")) {
      validateMail(payload);
    }
    String id =
        ProcessingIdentities.hash(
            "processing-v1",
            tenant,
            stage.name(),
            type.asString(),
            Integer.toString(version.asInt()),
            source.asString(),
            stage.processingVersion());
    var reference =
        SourceRecordReference.builder()
            .sourceId(source.asString())
            .eventType(type.asString())
            .schemaVersion(version.asInt())
            .topic(raw.topic())
            .partition(raw.partition())
            .offset(raw.offset())
            .build();
    return new Decoded(tenant, id, reference, payload);
  }

  private void validateMail(JsonNode payload) {
    for (String field : List.of("sender", "title", "date", "content", "contentType")) {
      if (!payload.has(field)) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
      }
      if (!payload.path(field).isNull() && !payload.path(field).isString()) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
      }
    }
    try {
      Instant.parse(payload.path("date").asString());
    } catch (RuntimeException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
    }
    String mediaType =
        payload.path("contentType").isNull() ? null : payload.path("contentType").asString();
    if (payload.path("content").isNull() != (mediaType == null)
        || (mediaType != null && !Set.of("text/plain", "text/html").contains(mediaType))) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_RECORD, false);
    }
  }

  private ProcessingDisposition invalid(
      ProcessingStage stage, RawEventRecord raw, ProcessingFailureReason reason) {
    return quarantine(stage, raw, null, null, reason);
  }

  private ProcessingDisposition quarantine(
      ProcessingStage stage,
      RawEventRecord raw,
      Decoded input,
      ProcessingEntry claim,
      ProcessingFailureReason reason) {
    String occurrence =
        ProcessingIdentities.hash(
            "failure-v1",
            stage.name(),
            stage.processingVersion(),
            raw.topic(),
            Integer.toString(raw.partition()),
            Long.toString(raw.offset()),
            claim == null ? "0" : Long.toString(claim.generation()));
    String id = input == null ? occurrence : input.id();
    ProcessingEntry entry =
        dao.quarantine(
            id,
            stage.name(),
            stage.processingVersion(),
            claim,
            clock.instant(),
            occurrence,
            reason.name(),
            raw.topic() + ":" + raw.partition() + ":" + raw.offset());
    if (entry.status() == ProcessingStatus.QUARANTINED) {
      return acknowledged();
    }
    if (entry.status() != ProcessingStatus.QUARANTINE_PENDING) {
      return waitUntil(entry.leaseUntil());
    }
    String location = raw.topic() + ":" + raw.partition() + ":" + raw.offset();
    if (!location.equals(entry.failureLocation())) {
      String[] coordinates = entry.failureLocation().split(":");
      raw =
          transport.read(
              coordinates[0], Integer.parseInt(coordinates[1]), Long.parseLong(coordinates[2]));
      if (input != null) {
        input = decode(stage, raw);
      }
    }
    var original =
        RawEventRecord.builder()
            .topic(raw.topic())
            .partition(raw.partition())
            .offset(raw.offset())
            .timestamp(raw.timestamp())
            .keyBase64(raw.keyBase64())
            .valueBase64(raw.valueBase64())
            .headers(Map.of())
            .build();
    var payload =
        QuarantinedRecord.builder()
            .failureId(entry.failureId())
            .stage(stage.name())
            .processingVersion(stage.processingVersion())
            .attempts(entry.attempts())
            .failedAt(entry.dueAt())
            .reason(entry.reason())
            .tenant(input == null ? null : input.tenant())
            .sourceId(input == null ? null : input.source().sourceId())
            .processingId(input == null ? null : input.id())
            .original(original)
            .build();
    byte[] bytes =
        mapper.writeValueAsBytes(new EventEnvelope<>("processor.quarantined", 1, payload));
    transport.publish(
        EventPublication.builder()
            .id(entry.failureId())
            .topic(stage.quarantineTopic())
            .keyBase64(raw.keyBase64())
            .valueBase64(ProcessingModelSupport.encode(bytes))
            .headers(Map.of())
            .build());
    if (!dao.confirmQuarantine(entry, clock.instant())) {
      return waitUntil(clock.instant().plus(properties.recoveryInterval()));
    }
    metrics.count(stage, "quarantined");
    log.warn(
        "Processor stage={} failureId={} reason={}",
        stage.name(),
        entry.failureId(),
        entry.reason());
    return acknowledged();
  }

  @Override
  public void recover(ProcessingStage stage) {
    try {
      for (ProcessingEntry entry : dao.pending(stage.name(), properties.outboxBatch())) {
        for (ProcessingOutput output : entry.outputs()) {
          if (!output.published()) {
            transport.publish(
                EventPublication.builder()
                    .id(output.id())
                    .topic(output.topic())
                    .keyBase64(output.keyBase64())
                    .valueBase64(output.valueBase64())
                    .headers(Map.of())
                    .build());
            metrics.count(stage, "produced");
            dao.published(entry.id(), output.id());
          }
        }
      }
      metrics.snapshot(stage, dao);
    } catch (ProcessingInfrastructureException | TransportUnavailableException failure) {
      metrics.gauge(stage, "paused", 1);
      log.warn("Processor stage={} reason=INFRASTRUCTURE_UNAVAILABLE", stage.name());
    }
  }

  private ProcessingDisposition acknowledged() {
    return new ProcessingDisposition(true, clock.instant());
  }

  private ProcessingDisposition waitUntil(Instant due) {
    return new ProcessingDisposition(false, due);
  }

  private record Decoded(
      String tenant, String id, SourceRecordReference source, JsonNode payload) {}
}
