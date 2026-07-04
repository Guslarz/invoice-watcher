package com.guslarz.invoicewatcher.mailconnector.api;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import lombok.Builder;

@Builder
public record MailConnectionProperties(
    String host,
    int port,
    MailCredentials mailCredentials
) {
}
