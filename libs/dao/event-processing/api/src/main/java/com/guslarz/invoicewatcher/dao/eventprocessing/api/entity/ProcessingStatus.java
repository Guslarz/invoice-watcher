package com.guslarz.invoicewatcher.dao.eventprocessing.api.entity;

public enum ProcessingStatus {
  RUNNING,
  RETRY,
  COMPLETE,
  QUARANTINE_PENDING,
  QUARANTINED
}
