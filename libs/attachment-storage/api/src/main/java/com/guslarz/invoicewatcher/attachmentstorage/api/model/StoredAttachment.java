package com.guslarz.invoicewatcher.attachmentstorage.api.model;

import lombok.Builder;

@Builder
public record StoredAttachment(String generation, String sha256, long byteLength) {}
