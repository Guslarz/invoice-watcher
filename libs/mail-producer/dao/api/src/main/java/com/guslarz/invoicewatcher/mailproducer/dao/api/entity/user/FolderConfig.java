package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record FolderConfig(
    String name,
    ZonedDateTime scanTimestamp
) {
}
