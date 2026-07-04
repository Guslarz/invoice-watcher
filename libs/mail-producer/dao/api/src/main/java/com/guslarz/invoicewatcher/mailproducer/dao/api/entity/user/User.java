package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import lombok.Builder;

@Builder
public record User(
    Long id,
    String name,
    MailServer mailServer,
    MailCredentials mailCredentials
) {
}
