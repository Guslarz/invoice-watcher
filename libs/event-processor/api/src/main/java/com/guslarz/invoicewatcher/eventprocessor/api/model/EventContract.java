package com.guslarz.invoicewatcher.eventprocessor.api.model;

/** Event type and schema version accepted or produced by a processing stage. */
public record EventContract(String eventType, int schemaVersion) {
  public EventContract {
    if (eventType == null
        || !ProcessingModelSupport.EVENT_TYPE.matcher(eventType).matches()
        || schemaVersion < 1) {
      throw new IllegalArgumentException("Invalid contract declaration");
    }
  }
}
