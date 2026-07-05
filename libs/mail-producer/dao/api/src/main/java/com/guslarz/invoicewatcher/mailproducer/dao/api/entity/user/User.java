package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import lombok.Builder;

import java.util.List;

@Builder
public record User(
    Long id,
    String name,
    List<MailScanConfig> scanConfigs
) {
}
