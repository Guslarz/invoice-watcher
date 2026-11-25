package com.guslarz.invoicewatcher.mailattachment.api.exception;

/** Allowlisted diagnostics only; never retain provider exception messages or causes. */
public final class AttachmentException extends RuntimeException {
  public AttachmentException(String reason) {
    super(reason);
  }
}
