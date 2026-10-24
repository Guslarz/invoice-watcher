package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import lombok.Builder;

import java.time.ZonedDateTime;

@Builder(toBuilder = true)
public record FolderConfig(
    String id,
    String name,
    ZonedDateTime startDate,
    Long uidValidity,
    Long lastUid
) {
}
