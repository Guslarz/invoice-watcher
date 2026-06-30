package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import lombok.Builder;

@Builder
public record MailServer(
    String host,
    int port
) {
}
