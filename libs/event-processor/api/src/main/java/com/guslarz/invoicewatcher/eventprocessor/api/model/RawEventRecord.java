package com.guslarz.invoicewatcher.eventprocessor.api.model;

import static com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingModelSupport.decode;

import java.util.Map;
import lombok.Builder;

/** Base64 preserves nullness and arbitrary original bytes without exposing mutable arrays. */
@Builder(toBuilder = true)
public record RawEventRecord(
    String topic,
    int partition,
    long offset,
    long timestamp,
    String keyBase64,
    String valueBase64,
    Map<String, String> headers) {
  public RawEventRecord {
    headers = Map.copyOf(headers);
  }

  public byte[] keyBytes() {
    return decode(keyBase64);
  }

  public byte[] valueBytes() {
    return decode(valueBase64);
  }

  @Override
  public String toString() {
    return "RawEventRecord[redacted]";
  }
}
