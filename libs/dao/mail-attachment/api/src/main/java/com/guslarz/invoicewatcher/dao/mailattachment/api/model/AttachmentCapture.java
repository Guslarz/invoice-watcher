package com.guslarz.invoicewatcher.dao.mailattachment.api.model;

import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record AttachmentCapture(
    String id,
    String ownerScope,
    String sourceId,
    int policyVersion,
    Instant capturedAt,
    Instant availableUntil,
    long fence,
    Instant leaseUntil,
    boolean expired,
    AttachmentManifest manifest,
    Instant deletedAt) {}
