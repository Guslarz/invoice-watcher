package com.guslarz.invoicewatcher.eventprocessor.api.exception;

import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingFailureReason;
import lombok.Getter;

/** Record-local classification with a fixed safe diagnostic. Never attach an external cause. */
@Getter
public class ProcessingFailure extends RuntimeException {
  private final ProcessingFailureReason reason;
  private final boolean retryable;

  public ProcessingFailure(ProcessingFailureReason reason, boolean retryable) {
    super(reason.name());
    this.reason = reason;
    this.retryable = retryable;
  }
}
