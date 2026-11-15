package com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user;

import java.time.ZonedDateTime;
import lombok.Builder;

@Builder(toBuilder = true)
public record FolderConfig(
    String id, String name, ZonedDateTime startDate, Long uidValidity, Long lastUid) {}
