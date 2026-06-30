package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.mailcredentials;

public record MailBasicAuthCredentials(
    String username,
    String password
) implements MailCredentials {
}
