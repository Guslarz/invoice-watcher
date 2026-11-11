package com.guslarz.invoicewatcher.eventproducer.kafka;

import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MailMessageSerializationTest {

  @ParameterizedTest
  @CsvSource({"<p>Invoice</p>,text/html", "Invoice,text/plain", "'',text/plain", ","})
  @DisplayName("EVENT-004: JSON contains the selected body and matching contentType, including nulls")
  void serializesBodyAndMediaType(String content, String contentType) {
    // given
    MailMessage message = MailMessage.builder()
        .sender("billing@shop.example")
        .title("Invoice 10/2026")
        .date(ZonedDateTime.parse("2026-10-13T08:15:00Z"))
        .content(content)
        .contentType(contentType)
        .build();
    try (JacksonJsonSerializer<MailMessage> uut = new JacksonJsonSerializer<>()) {
      // when
      byte[] serialized = uut.serialize("all-mails", message);

      // then
      JsonMapper mapper = JsonMapper.builder().build();
      JsonNode json = mapper.readTree(serialized);
      assertThat(json.size()).isEqualTo(5);
      assertThat(json.get("sender").asString()).isEqualTo(message.sender());
      assertThat(json.get("title").asString()).isEqualTo(message.title());
      assertThat(json.get("date").asString()).isEqualTo("2026-10-13T08:15:00Z");
      assertThat(json.get("content")).isEqualTo(mapper.valueToTree(content));
      assertThat(json.get("contentType")).isEqualTo(mapper.valueToTree(contentType));
    }
  }
}
