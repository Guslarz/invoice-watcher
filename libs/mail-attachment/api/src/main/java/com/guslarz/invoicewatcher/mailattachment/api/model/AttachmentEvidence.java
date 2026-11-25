package com.guslarz.invoicewatcher.mailattachment.api.model;

import java.time.Instant;
import lombok.Builder;

@Builder
public record AttachmentEvidence(
    String store,
    String objectKey,
    String generation,
    String sha256,
    long byteLength,
    Instant capturedAt,
    Instant availableUntil) {}
