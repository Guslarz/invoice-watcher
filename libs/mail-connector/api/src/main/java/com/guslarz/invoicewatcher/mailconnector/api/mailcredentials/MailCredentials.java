package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

public interface MailCredentials {

  <T> T accept(MailCredentialsVisitor<T> converter);
}
