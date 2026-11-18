package com.guslarz.invoicewatcher.eventprocessor.api.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Transport size failure with safe numeric diagnostics; input remains unacknowledged. */
@Getter
@RequiredArgsConstructor
public class PublicationTooLargeException extends TransportUnavailableException {
  private final long actualBytes;
  private final long limitBytes;
}
