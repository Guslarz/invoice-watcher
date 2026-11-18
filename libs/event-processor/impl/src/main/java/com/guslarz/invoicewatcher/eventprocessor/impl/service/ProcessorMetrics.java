package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class ProcessorMetrics {
  private final MeterRegistry registry;

  @Qualifier("processorClock")
  private final Clock clock;

  private final Map<String, AtomicReference<Double>> values = new ConcurrentHashMap<>();

  void count(ProcessingStage stage, String outcome) {
    registry
        .counter("invoice.processor.deliveries", "stage", stage.name(), "outcome", outcome)
        .increment();
  }

  void review(ProcessingStage stage, String reason) {
    registry
        .counter("invoice.processor.review", "stage", stage.name(), "reason", reason)
        .increment();
  }

  void duration(ProcessingStage stage, long nanos) {
    registry
        .timer("invoice.processor.handler.duration", "stage", stage.name())
        .record(nanos, TimeUnit.NANOSECONDS);
  }

  void gauge(ProcessingStage stage, String name, double value) {
    String key = stage.name() + ":" + name;
    values
        .computeIfAbsent(
            key,
            unused -> {
              var holder = new AtomicReference<>(Double.NaN);
              registry.gauge(
                  "invoice.processor." + name,
                  List.of(Tag.of("stage", stage.name())),
                  holder,
                  ref -> ref.get());
              return holder;
            })
        .set(value);
  }

  void snapshot(ProcessingStage stage, ProcessingDao dao) {
    var entries = dao.entries(stage.name());
    double last =
        entries.stream()
            .filter(e -> e.completedAt() != null)
            .mapToDouble(e -> e.completedAt().toEpochMilli() / 1000.0)
            .max()
            .orElse(Double.NaN);
    var pending =
        entries.stream().filter(e -> e.outputs().stream().anyMatch(o -> !o.published())).toList();
    gauge(stage, "last.success", last);
    gauge(
        stage,
        "outbox.pending",
        pending.stream()
            .mapToLong(e -> e.outputs().stream().filter(o -> !o.published()).count())
            .sum());
    gauge(
        stage,
        "outbox.oldest.age",
        pending.stream()
            .mapToDouble(e -> Math.max(0, clock.millis() - e.completedAt().toEpochMilli()) / 1000.0)
            .max()
            .orElse(0));
  }
}
