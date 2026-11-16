package com.guslarz.invoicewatcher.mailproducer.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.guslarz.invoicewatcher.eventproducer.api.EventEnvelope;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import java.time.ZonedDateTime;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MailEventSerializationTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @ParameterizedTest
  @CsvSource({"<p>Invoice</p>,text/html", "Invoice,text/plain", "'',text/plain", ","})
  @DisplayName("EVENT-004: the versioned mail payload retains metadata and absent or empty bodies")
  void serializesMailPayload(String content, String contentType) {
    // given
    MailMessage message = new MailMessage("billing@shop.example", "Invoice 10/2026",
        ZonedDateTime.parse("2026-10-13T08:15:00Z"), content, contentType);
    String sourceId = MailSourceIdentity.create(
        MailSourceIdentityTest.context("tenant", "host", 993, "private-login", "INBOX"), 7, 13);
    MailIngestedPayload payload = MailIngestedPayload.from(sourceId, message);
    EventEnvelope<MailIngestedPayload> event = new EventEnvelope<>("mail.ingested", 1, payload);
    try (JacksonJsonSerializer<EventEnvelope<MailIngestedPayload>> uut =
        new JacksonJsonSerializer<>()) {
      // when
      JsonNode json = mapper.readTree(uut.serialize("all-mails", event));

      // then
      assertThat(json.size()).isEqualTo(3);
      assertThat(json.get("eventType").asString()).isEqualTo("mail.ingested");
      assertThat(json.get("schemaVersion").isIntegralNumber()).isTrue();
      assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
      JsonNode body = json.get("payload");
      assertThat(body.size()).isEqualTo(6);
      assertThat(body.get("sourceId").asString()).isEqualTo(sourceId);
      assertThat(body.get("sender").asString()).isEqualTo(message.sender());
      assertThat(body.get("title").asString()).isEqualTo(message.title());
      assertThat(body.get("date").asString()).isEqualTo("2026-10-13T08:15:00Z");
      assertThat(body.get("content")).isEqualTo(mapper.valueToTree(content));
      assertThat(body.get("contentType")).isEqualTo(mapper.valueToTree(contentType));
      assertThat(json.toString()).doesNotContain("private-login");
    }
  }

  @Test
  @DisplayName("EVENT-007: consumers dispatch by type/version and tolerate unknown fields at both levels")
  void dispatchesWithoutDependingOnJavaTypes() {
    // given
    String record = """
        {"eventType":"mail.ingested","schemaVersion":1,"futureMetadata":true,
         "payload":{"sourceId":"imap-v1:example","title":"Invoice","futureFact":42}}
        """;

    // when
    JsonNode uut = readSupportedMail(record);

    // then
    assertThat(uut.get("sourceId").asString()).isEqualTo("imap-v1:example");
    assertThat(uut.get("title").asString()).isEqualTo("Invoice");
  }

  @ParameterizedTest
  @CsvSource({"mail.ingested,2", "invoice.recognized,1"})
  @DisplayName("EVENT-007: unknown event types and versions are explicitly unsupported")
  void rejectsUnsupportedContracts(String type, int version) {
    // given
    String record = mapper.writeValueAsString(new EventEnvelope<>(type, version, "payload"));

    // when
    Throwable uut = catchThrowable(() -> readSupportedMail(record));

    // then
    assertThat(uut).isInstanceOf(IllegalArgumentException.class).hasMessage("Unsupported contract");
  }

  @Test
  @DisplayName("EVENT-007: retained flat records remain legacy with no fabricated source identity")
  void distinguishesLegacyRecords() {
    // given
    String record = """
        {"sender":"shop","title":"Invoice","date":null,"content":null,"contentType":null}
        """;

    // when
    Throwable uut = catchThrowable(() -> readSupportedMail(record));

    // then
    assertThat(uut).isInstanceOf(IllegalArgumentException.class).hasMessage("Legacy unversioned mail");
    assertThat(mapper.readTree(record).has("sourceId")).isFalse();
  }

  @Test
  @DisplayName("EVENT-007: the same envelope serializes other payload types without class headers")
  void serializesAnotherPayloadType() {
    // given
    EventEnvelope<ExamplePayload> event = new EventEnvelope<>("example.created", 1,
        new ExamplePayload("example-42"));
    RecordHeaders headers = new RecordHeaders();
    try (JacksonJsonSerializer<EventEnvelope<ExamplePayload>> uut = new JacksonJsonSerializer<>()) {
      uut.setAddTypeInfo(false);

      // when
      JsonNode json = mapper.readTree(uut.serialize("example", headers, event));

      // then
      assertThat(json.get("eventType").asString()).isEqualTo("example.created");
      assertThat(json.get("payload").get("reference").asString()).isEqualTo("example-42");
      assertThat(headers.toArray()).isEmpty();
    }
  }

  // Consumer fixture only: this change does not introduce a production consumer.
  private JsonNode readSupportedMail(String record) {
    JsonNode json = mapper.readTree(record);
    if (!json.has("eventType") && !json.has("schemaVersion")) {
      throw new IllegalArgumentException("Legacy unversioned mail");
    }
    if (!"mail.ingested".equals(json.path("eventType").asString())
        || json.path("schemaVersion").asInt() != 1) {
      throw new IllegalArgumentException("Unsupported contract");
    }
    return json.get("payload");
  }

  public record ExamplePayload(String reference) {}
}
