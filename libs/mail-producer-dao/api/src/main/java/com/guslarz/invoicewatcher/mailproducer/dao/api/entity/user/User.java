package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.mailcredentials.MailCredentials;
import lombok.Builder;

@Builder
public record User(
    Long id,
    MailServer mailServer,
    MailCredentials mailCredentials
) {
}
