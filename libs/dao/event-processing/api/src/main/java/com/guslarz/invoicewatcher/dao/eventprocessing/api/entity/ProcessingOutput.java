package com.guslarz.invoicewatcher.dao.eventprocessing.api.entity;

import lombok.Builder;

/** Serialized immutable output. Sensitive bytes are never rendered by toString. */
@Builder(toBuilder = true)
public record ProcessingOutput(
    String id, String topic, String keyBase64, String valueBase64, boolean published) {
  public ProcessingOutput confirmed() {
    return toBuilder().keyBase64(null).valueBase64(null).published(true).build();
  }

  @Override
  public String toString() {
    return "ProcessingOutput[redacted]";
  }
}
