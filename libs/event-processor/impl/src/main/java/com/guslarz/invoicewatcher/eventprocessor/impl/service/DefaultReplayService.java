package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.entity.ReplayAudit;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.ProcessingFailure;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventPublication;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingFailureReason;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.model.QuarantinedRecord;
import com.guslarz.invoicewatcher.eventprocessor.api.model.RawEventRecord;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ReplayPreview;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ReplayRequest;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ReplayProperties;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorTransport;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ReplayService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Reusable operations service; the adopting application authenticates the caller. */
@Component
public final class DefaultReplayService implements ReplayService {
  private final ProcessingDao dao;
  private final ProcessorTransport transport;
  private final List<ProcessingStage> stages;
  private final ReplayProperties properties;
  private final Clock clock;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public DefaultReplayService(
      ProcessingDao dao,
      ProcessorTransport transport,
      List<ProcessingStage> stages,
      ReplayProperties properties,
      @Qualifier("processorClock") Clock clock) {
    this.dao = dao;
    this.transport = transport;
    this.stages = List.copyOf(stages);
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public ReplayPreview preview(ReplayRequest request) {
    ProcessingStage stage = stage(request);
    List<Long> offsets = new ArrayList<>();
    for (long offset = request.fromOffset(); offset < request.toOffset(); offset++) {
      quarantine(
          transport.read(stage.quarantineTopic(), request.partition(), offset), stage, mapper);
      offsets.add(offset);
    }
    return new ReplayPreview(offsets.size(), offsets, offsets.size() / request.rate());
  }

  @Override
  public synchronized void execute(ReplayRequest request) {
    ProcessingStage stage = stage(request);
    ReplayAudit audit =
        dao.authorize(
            ReplayAudit.builder()
                .recordVersion(1)
                .id(request.requestId())
                .stage(stage.name())
                .processingVersion(request.processingVersion())
                .topic(stage.quarantineTopic())
                .partition(request.partition())
                .fromOffset(request.fromOffset())
                .toOffset(request.toOffset())
                .limit(request.limit())
                .rate(request.rate())
                .operator(request.operator())
                .reason(request.reason())
                .createdAt(clock.instant())
                .nextOffset(request.fromOffset())
                .finished(false)
                .failure(null)
                .build());
    if (audit.failure() != null) {
      throw new ProcessingFailure(ProcessingFailureReason.EVIDENCE_EXPIRED, false);
    }
    if (audit.finished()) {
      return;
    }
    for (long offset = audit.nextOffset(); offset < audit.toOffset(); offset++) {
      QuarantinedRecord quarantine;
      try {
        quarantine =
            quarantine(transport.read(audit.topic(), audit.partition(), offset), stage, mapper);
      } catch (ProcessingFailure failure) {
        dao.replayProgress(audit.id(), offset, true, failure.reason().name());
        throw failure;
      }
      Map<String, String> headers =
          Map.of(
              "iw-replay-request",
              audit.id(),
              "iw-replay-partition",
              Integer.toString(audit.partition()),
              "iw-replay-offset",
              Long.toString(offset));
      transport.publish(
          EventPublication.builder()
              .id(audit.id())
              .topic(stage.replayTopic())
              .keyBase64(quarantine.original().keyBase64())
              .valueBase64(quarantine.original().valueBase64())
              .headers(headers)
              .build());
      dao.replayProgress(audit.id(), offset + 1, offset + 1 == audit.toOffset(), null);
      if (offset + 1 < audit.toOffset()) {
        LockSupport.parkNanos((long) (1_000_000_000 / audit.rate()));
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
      }
    }
  }

  private ProcessingStage stage(ReplayRequest request) {
    if (request.limit() > properties.maxRecords() || request.rate() > properties.maxRate()) {
      throw new IllegalArgumentException("Replay exceeds configured bounds");
    }
    return stages.stream()
        .filter(
            stage ->
                stage.name().equals(request.stage())
                    && stage.processingVersion().equals(request.processingVersion()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Undeclared replay stage or processing version"));
  }

  static RawEventRecord original(
      ProcessingStage stage,
      RawEventRecord delivered,
      ProcessingDao dao,
      ProcessorTransport transport,
      JsonMapper mapper) {
    try {
      String request = delivered.headers().get("iw-replay-request");
      ProcessingModelSupport.identifier(request);
      ReplayAudit audit =
          dao.replay(request)
              .orElseThrow(
                  () -> new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false));
      int partition = Integer.parseInt(delivered.headers().get("iw-replay-partition"));
      long offset = Long.parseLong(delivered.headers().get("iw-replay-offset"));
      if (!audit.stage().equals(stage.name())
          || !audit.processingVersion().equals(stage.processingVersion())
          || !audit.topic().equals(stage.quarantineTopic())
          || audit.partition() != partition
          || offset < audit.fromOffset()
          || offset >= audit.toOffset()) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false);
      }
      var evidence = quarantine(transport.read(audit.topic(), partition, offset), stage, mapper);
      RawEventRecord original = evidence.original();
      if (!Objects.equals(original.keyBase64(), delivered.keyBase64())
          || !Objects.equals(original.valueBase64(), delivered.valueBase64())) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false);
      }
      return original;
    } catch (IllegalArgumentException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false);
    }
  }

  private static QuarantinedRecord quarantine(
      RawEventRecord raw, ProcessingStage stage, JsonMapper mapper) {
    try {
      var envelope = mapper.readTree(raw.valueBytes());
      if (!envelope.path("eventType").asString().equals("processor.quarantined")
          || !envelope.path("schemaVersion").isIntegralNumber()
          || envelope.path("schemaVersion").asInt() != 1) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false);
      }
      QuarantinedRecord quarantine =
          mapper.treeToValue(envelope.path("payload"), QuarantinedRecord.class);
      if (!quarantine.stage().equals(stage.name()) || quarantine.original() == null) {
        throw new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false);
      }
      return quarantine;
    } catch (ProcessingFailure failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ProcessingFailure(ProcessingFailureReason.INVALID_REPLAY, false);
    }
  }
}
