package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.time.Instant;

/** Consumer acknowledgement and resume decision after durable record processing. */
public record ProcessingDisposition(boolean acknowledge, Instant resumeAt) {}
