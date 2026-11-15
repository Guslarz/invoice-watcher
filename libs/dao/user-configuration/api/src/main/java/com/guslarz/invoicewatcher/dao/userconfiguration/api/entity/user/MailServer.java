package com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user;

import lombok.Builder;

@Builder
public record MailServer(String host, int port) {}
