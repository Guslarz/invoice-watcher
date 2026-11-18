package com.guslarz.invoicewatcher.eventprocessor.kafka.service;

import com.guslarz.invoicewatcher.eventprocessor.api.exception.ProcessingFailure;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.PublicationTooLargeException;
import com.guslarz.invoicewatcher.eventprocessor.api.exception.TransportUnavailableException;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventPublication;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingFailureReason;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.model.RawEventRecord;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorTransport;
import com.guslarz.invoicewatcher.eventprocessor.kafka.properties.ProcessorKafkaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/**
 * Byte-oriented Kafka adapter; malformed payloads cannot fail in a deserializer before quarantine.
 */
public final class KafkaProcessorTransport implements ProcessorTransport, AutoCloseable {
  private final ProcessorKafkaProperties properties;
  private final Map<String, Object> common;
  private final KafkaProducer<byte[], byte[]> producer;
  private final Admin admin;

  public KafkaProcessorTransport(
      ProcessorKafkaProperties properties, Map<String, Object> clientProperties) {
    this.properties = properties;
    common = new HashMap<>(clientProperties);
    common.put("bootstrap.servers", properties.bootstrapServers());
    common.put("default.api.timeout.ms", 10000);
    common.put("request.timeout.ms", 10000);
    var config = new HashMap<>(common);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, properties.maxPublicationBytes());
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15000);
    producer = new KafkaProducer<>(config);
    admin = Admin.create(common);
  }

  Map<String, Object> consumerConfig(String group) {
    var config = new HashMap<>(common);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.offsetReset());
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
    return config;
  }

  @Override
  public void preflight(ProcessingStage stage) {
    try {
      List<String> topics =
          List.of(
              stage.inputTopic(),
              stage.resultTopic(),
              stage.quarantineTopic(),
              stage.replayTopic());
      admin.describeTopics(topics).allTopicNames().get(10, TimeUnit.SECONDS);
      var resource = new ConfigResource(ConfigResource.Type.TOPIC, stage.quarantineTopic());
      var config =
          admin.describeConfigs(List.of(resource)).all().get(10, TimeUnit.SECONDS).get(resource);
      long retention = Long.parseLong(config.get("retention.ms").value());
      long retentionBytes = Long.parseLong(config.get("retention.bytes").value());
      int maxBytes = Integer.parseInt(config.get("max.message.bytes").value());
      if (!config.get("cleanup.policy").value().equals("delete")
          || retention != properties.quarantineRetentionMs()
          || retentionBytes != -1
          || maxBytes < properties.maxPublicationBytes()) {
        throw new IllegalArgumentException(
            "Unsuitable processor quarantine retention or size configuration");
      }
      var inputResource = new ConfigResource(ConfigResource.Type.TOPIC, stage.inputTopic());
      var input =
          admin
              .describeConfigs(List.of(inputResource))
              .all()
              .get(10, TimeUnit.SECONDS)
              .get(inputResource);
      long inputMax = Long.parseLong(input.get("max.message.bytes").value());
      if (inputMax * 7 / 3 + 8192 > properties.maxPublicationBytes()) {
        throw new IllegalArgumentException(
            "QuarantinedRecord capacity is too small for the source record limit");
      }
      var replayResource = new ConfigResource(ConfigResource.Type.TOPIC, stage.replayTopic());
      var replay =
          admin
              .describeConfigs(List.of(replayResource))
              .all()
              .get(10, TimeUnit.SECONDS)
              .get(replayResource);
      if (Long.parseLong(replay.get("max.message.bytes").value()) < inputMax + 8192) {
        throw new IllegalArgumentException("Processor replay topic capacity is too small");
      }
      var resultResource = new ConfigResource(ConfigResource.Type.TOPIC, stage.resultTopic());
      var result =
          admin
              .describeConfigs(List.of(resultResource))
              .all()
              .get(10, TimeUnit.SECONDS)
              .get(resultResource);
      if (Long.parseLong(result.get("max.message.bytes").value()) < 524288 + 8192) {
        throw new IllegalArgumentException("Processor result topic capacity is too small");
      }
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new TransportUnavailableException();
    } catch (ExecutionException | TimeoutException | RuntimeException failure) {
      throw new TransportUnavailableException();
    }
  }

  @Override
  public void publish(EventPublication publication) {
    byte[] key = ProcessingModelSupport.decode(publication.keyBase64());
    byte[] value = ProcessingModelSupport.decode(publication.valueBase64());
    long length = (key == null ? 0 : key.length) + (value == null ? 0 : value.length) + 1024L;
    if (length > properties.maxPublicationBytes()) {
      throw new PublicationTooLargeException(length, properties.maxPublicationBytes());
    }
    var record = new ProducerRecord<byte[], byte[]>(publication.topic(), key, value);
    publication
        .headers()
        .forEach(
            (name, text) -> {
              if (!Set.of("iw-replay-request", "iw-replay-partition", "iw-replay-offset")
                  .contains(name)) {
                throw new IllegalArgumentException("Unsupported processor publication header");
              }
              record.headers().add(name, text.getBytes(StandardCharsets.UTF_8));
            });
    try {
      producer.send(record).get(20, TimeUnit.SECONDS);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new TransportUnavailableException();
    } catch (ExecutionException | TimeoutException | RuntimeException failure) {
      throw new TransportUnavailableException();
    }
  }

  @Override
  public RawEventRecord read(String topic, int partition, long offset) {
    var tp = new TopicPartition(topic, partition);
    try (var consumer =
        new KafkaConsumer<byte[], byte[]>(consumerConfig("processor-evidence-reader"))) {
      consumer.assign(List.of(tp));
      long beginning = consumer.beginningOffsets(List.of(tp)).get(tp);
      long end = consumer.endOffsets(List.of(tp)).get(tp);
      if (offset < beginning || offset >= end) {
        throw new ProcessingFailure(ProcessingFailureReason.EVIDENCE_EXPIRED, false);
      }
      consumer.seek(tp, offset);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<byte[], byte[]> record : consumer.poll(Duration.ofMillis(100))) {
          if (record.offset() == offset) {
            return raw(record);
          }
          if (record.offset() > offset) {
            throw new ProcessingFailure(ProcessingFailureReason.EVIDENCE_EXPIRED, false);
          }
        }
      }
      throw new TransportUnavailableException();
    } catch (ProcessingFailure failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new TransportUnavailableException();
    }
  }

  @Override
  public Map<Integer, Long> lag(String topic, String group) {
    try {
      var topicInfo =
          admin.describeTopics(List.of(topic)).allTopicNames().get(10, TimeUnit.SECONDS).get(topic);
      Map<TopicPartition, OffsetSpec> requests = new HashMap<>();
      topicInfo
          .partitions()
          .forEach(
              p -> requests.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));
      var end = admin.listOffsets(requests).all().get(10, TimeUnit.SECONDS);
      var committed =
          admin
              .listConsumerGroupOffsets(group)
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS);
      Map<Integer, Long> lag = new HashMap<>();
      end.forEach(
          (partition, info) ->
              lag.put(
                  partition.partition(),
                  committed.containsKey(partition)
                      ? Math.max(0, info.offset() - committed.get(partition).offset())
                      : -1));
      return Map.copyOf(lag);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new TransportUnavailableException();
    } catch (ExecutionException | TimeoutException | RuntimeException failure) {
      throw new TransportUnavailableException();
    }
  }

  static RawEventRecord raw(ConsumerRecord<byte[], byte[]> record) {
    Map<String, String> headers = new HashMap<>();
    for (String name : List.of("iw-replay-request", "iw-replay-partition", "iw-replay-offset")) {
      var header = record.headers().lastHeader(name);
      if (header != null && header.value() != null && header.value().length <= 64) {
        headers.put(name, new String(header.value(), StandardCharsets.UTF_8));
      }
    }
    return RawEventRecord.builder()
        .topic(record.topic())
        .partition(record.partition())
        .offset(record.offset())
        .timestamp(record.timestamp())
        .keyBase64(ProcessingModelSupport.encode(record.key()))
        .valueBase64(ProcessingModelSupport.encode(record.value()))
        .headers(headers)
        .build();
  }

  @Override
  public void close() {
    producer.close(Duration.ofSeconds(5));
    admin.close(Duration.ofSeconds(5));
  }
}
