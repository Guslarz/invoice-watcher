package com.guslarz.invoicewatcher.eventprocessor.api.model;

/** Business outcome of a stage result; technical retries and quarantine are separate. */
public enum ProcessingOutcome {
  COMPLETED,
  REVIEW_REQUIRED
}
