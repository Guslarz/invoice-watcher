package com.guslarz.invoicewatcher.mailattachment.api.model;

import lombok.Builder;

@Builder
public record AttachmentDescriptor(
    String attachmentId,
    String partPath,
    String declaredContentType,
    String outcome,
    AttachmentEvidence evidence) {}
