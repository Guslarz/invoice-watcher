package com.guslarz.invoicewatcher.eventprocessor.api.model;

/** Bounded technical failure classifications for retry and quarantine decisions. */
public enum ProcessingFailureReason {
  INVALID_RECORD,
  UNSUPPORTED_CONTRACT,
  LEGACY_RECORD,
  INVALID_TENANT,
  INVALID_SOURCE,
  HANDLER_FAILURE,
  PERMANENT_FAILURE,
  RETRY_EXHAUSTED,
  HANDLER_TIMEOUT,
  RESULT_TOO_LARGE,
  INVALID_RESULT,
  INFRASTRUCTURE_UNAVAILABLE,
  INVALID_REPLAY,
  EVIDENCE_EXPIRED,
  QUARANTINE_TOO_LARGE
}
