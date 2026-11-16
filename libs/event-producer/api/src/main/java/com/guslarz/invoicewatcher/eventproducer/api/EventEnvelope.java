package com.guslarz.invoicewatcher.eventproducer.api;

/** A wire contract identified independently of Java payload classes. */
public record EventEnvelope<T>(String eventType, int schemaVersion, T payload) {

  public EventEnvelope {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("Event type is required");
    }
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("Schema version must be positive");
    }
    if (payload == null) {
      throw new IllegalArgumentException("Event payload is required");
    }
  }
}
