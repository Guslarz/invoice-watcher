package com.guslarz.invoicewatcher.eventprocessor.kafka.service;

import com.guslarz.invoicewatcher.eventprocessor.api.exception.TransportUnavailableException;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.properties.ProcessorProperties;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorRuntime;
import com.guslarz.invoicewatcher.eventprocessor.kafka.properties.ProcessorKafkaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.stereotype.Component;

@Component
public final class ProcessorKafkaLifecycle implements SmartLifecycle {
  private final List<ProcessingStage> stages;
  private final KafkaProcessorTransport transport;
  private final ProcessorRuntime runtime;
  private final ProcessorProperties runtimeProperties;
  private final ProcessorKafkaProperties properties;
  private final MeterRegistry registry;
  private final List<ConcurrentMessageListenerContainer<byte[], byte[]>> containers =
      new ArrayList<>();
  private final ConcurrentHashMap<String, AtomicLong> lags = new ConcurrentHashMap<>();
  private ScheduledExecutorService scheduler;
  private volatile boolean running;

  ProcessorKafkaLifecycle(
      List<ProcessingStage> stages,
      KafkaProcessorTransport transport,
      ProcessorRuntime runtime,
      ProcessorProperties runtimeProperties,
      ProcessorKafkaProperties properties,
      MeterRegistry registry) {
    this.stages = List.copyOf(stages);
    this.transport = transport;
    this.runtime = runtime;
    this.runtimeProperties = runtimeProperties;
    this.properties = properties;
    this.registry = registry;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    var names = new HashSet<String>();
    var reserved = new HashSet<String>();
    for (ProcessingStage stage : stages) {
      if (!names.add(stage.name())
          || !reserved.add(stage.quarantineTopic())
          || !reserved.add(stage.replayTopic())
          || !reserved.add(stage.resultTopic())) {
        throw new IllegalArgumentException("Conflicting processor stage declarations");
      }
      transport.preflight(stage);
    }
    try {
      for (ProcessingStage stage : stages) {
        var config = transport.consumerConfig(properties.groupPrefix() + stage.name());
        var containerProperties = new ContainerProperties(stage.inputTopic(), stage.replayTopic());
        containerProperties.setGroupId(properties.groupPrefix() + stage.name());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setSyncCommits(true);
        containerProperties.setPollTimeout(100);
        containerProperties.setMessageListener(
            (AcknowledgingMessageListener<byte[], byte[]>)
                (record, ack) -> {
                  var outcome = runtime.process(stage, KafkaProcessorTransport.raw(record));
                  if (outcome.acknowledge()) {
                    ack.acknowledge();
                  } else {
                    ack.nack(
                        Duration.ofMillis(
                            Math.max(
                                100,
                                Duration.between(Instant.now(), outcome.resumeAt()).toMillis())));
                  }
                });
        var container =
            new ConcurrentMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<byte[], byte[]>(config), containerProperties);
        container.setConcurrency(properties.concurrency());
        container.setCommonErrorHandler(new CommonContainerStoppingErrorHandler());
        containers.add(container);
        container.start();
      }
      scheduler =
          Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
      scheduler.scheduleWithFixedDelay(
          this::recover, 0, runtimeProperties.outboxInterval().toMillis(), TimeUnit.MILLISECONDS);
      running = true;
    } catch (RuntimeException failure) {
      stop();
      throw failure;
    }
  }

  private void recover() {
    for (ProcessingStage stage : stages) {
      runtime.recover(stage);
      for (String topic : List.of(stage.inputTopic(), stage.replayTopic())) {
        try {
          transport
              .lag(topic, properties.groupPrefix() + stage.name())
              .forEach(
                  (partition, value) -> {
                    String key = stage.name() + ":" + topic + ":" + partition;
                    lags.computeIfAbsent(
                            key,
                            unused -> {
                              var holder = new AtomicLong(-1);
                              registry.gauge(
                                  "invoice.processor.lag",
                                  List.of(
                                      Tag.of("stage", stage.name()),
                                      Tag.of("topic", topic),
                                      Tag.of("partition", partition.toString())),
                                  holder,
                                  ref -> ref.get() < 0 ? Double.NaN : ref.get());
                              return holder;
                            })
                        .set(value);
                  });
        } catch (TransportUnavailableException failure) {
          String prefix = stage.name() + ":" + topic + ":";
          lags.forEach(
              (key, value) -> {
                if (key.startsWith(prefix)) {
                  value.set(-1);
                }
              });
        }
      }
    }
  }

  @Override
  public synchronized void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    containers.forEach(ConcurrentMessageListenerContainer::stop);
    containers.clear();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
