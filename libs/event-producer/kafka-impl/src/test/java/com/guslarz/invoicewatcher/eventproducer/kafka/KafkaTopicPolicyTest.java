package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.testing.testcontainers.SharedTestcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
class KafkaTopicPolicyTest {
  @Container private static KafkaContainer kafka = SharedTestcontainers.kafka();

  @Test
  @DisplayName("EVENT-014: effective seven-day ingestion retention is checked before publishing")
  void rejectsExistingTopicPolicyDrift() throws Exception {
    // given
    String topic = "mail-" + UUID.randomUUID();
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    var uut =
        new KafkaEventProducer(
            template, new KafkaEventProducerProperties(kafka.getBootstrapServers()));
    try (Admin admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(topic, 1, (short) 1)
                      .configs(
                          Map.of(
                              "retention.ms",
                              "604800000",
                              "retention.bytes",
                              "-1",
                              "cleanup.policy",
                              "delete",
                              "message.timestamp.type",
                              "LogAppendTime"))))
          .all()
          .get(15, TimeUnit.SECONDS);
      // when
      var accepted = catchThrowable(() -> uut.verifyTopic(topic, 604800000));
      var resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
      admin
          .incrementalAlterConfigs(
              Map.of(
                  resource,
                  List.of(
                      new AlterConfigOp(
                          new ConfigEntry("retention.ms", "3600000"), AlterConfigOp.OpType.SET))))
          .all()
          .get(15, TimeUnit.SECONDS);
      var rejected = catchThrowable(() -> uut.verifyTopic(topic, 604800000));
      // then
      assertThat(accepted).isNull();
      assertThat(rejected).hasMessage("Unsuitable mail topic retention settings");
    }
  }
}
