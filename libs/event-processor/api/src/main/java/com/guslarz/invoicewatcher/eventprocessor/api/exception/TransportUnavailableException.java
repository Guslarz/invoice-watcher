package com.guslarz.invoicewatcher.eventprocessor.api.exception;

/** Safe shared broker failure with no provider message or cause. */
public class TransportUnavailableException extends RuntimeException {
  public TransportUnavailableException() {
    super("Processor transport unavailable");
  }
}
