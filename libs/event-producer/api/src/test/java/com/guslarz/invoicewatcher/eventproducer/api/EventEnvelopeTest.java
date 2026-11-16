package com.guslarz.invoicewatcher.eventproducer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventEnvelopeTest {

  @ParameterizedTest
  @CsvSource({",1,payload", "' ',1,payload", "mail.ingested,0,payload",
      "mail.ingested,-1,payload", "mail.ingested,1,"})
  @DisplayName("EVENT-007: envelope metadata requires a type, positive version and payload")
  void rejectsIncompleteEnvelope(String type, int version, String payload) {
    // given
    // when
    Throwable uut = catchThrowable(() -> new EventEnvelope<>(type, version, payload));

    // then
    assertThat(uut).isInstanceOf(IllegalArgumentException.class);
  }
}
