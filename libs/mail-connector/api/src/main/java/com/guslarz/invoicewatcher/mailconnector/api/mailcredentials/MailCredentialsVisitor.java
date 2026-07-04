package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

public interface MailCredentialsVisitor<T> {

  T visit(MailCredentials mailCredentials);

  T visitBasicMailCredentials(BasicAuthMailCredentials mailCredentials);
}
