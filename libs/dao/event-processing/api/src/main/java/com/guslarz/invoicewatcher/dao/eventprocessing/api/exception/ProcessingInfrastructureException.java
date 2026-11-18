package com.guslarz.invoicewatcher.dao.eventprocessing.api.exception;

/** Safe operational failure; deliberately excludes the provider's message and cause. */
public class ProcessingInfrastructureException extends RuntimeException {
  public ProcessingInfrastructureException() {
    super("Processing storage unavailable");
  }
}
