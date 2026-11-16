package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

public interface MailCredentialsVisitor<T> {

  T visitBasicMailCredentials(BasicAuthMailCredentials mailCredentials);

  T visitGmailOauthCredentials(GmailOauthCredentials credentials);
}
