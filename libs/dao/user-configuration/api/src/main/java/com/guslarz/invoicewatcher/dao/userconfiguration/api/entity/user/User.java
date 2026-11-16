package com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user;

import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record User(
    String id,
    String name,
    String ownerSubject,
    Long configurationVersion,
    List<MailScanConfig> scanConfigs) {

  public User {
    scanConfigs = scanConfigs == null ? null : List.copyOf(scanConfigs);
  }
}
