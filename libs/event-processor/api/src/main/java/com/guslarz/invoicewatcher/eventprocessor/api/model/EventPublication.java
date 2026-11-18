package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record EventPublication(
    String id, String topic, String keyBase64, String valueBase64, Map<String, String> headers) {
  public EventPublication {
    headers = Map.copyOf(headers);
  }

  @Override
  public String toString() {
    return "EventPublication[redacted]";
  }
}
