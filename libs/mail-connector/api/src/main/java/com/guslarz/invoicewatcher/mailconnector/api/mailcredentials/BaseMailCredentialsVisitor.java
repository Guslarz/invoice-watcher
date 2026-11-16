package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

public abstract class BaseMailCredentialsVisitor<T> implements MailCredentialsVisitor<T> {

  public T visit(MailCredentials mailCredentials) {
    return mailCredentials.accept(this);
  }

  @Override
  public T visitGmailOauthCredentials(GmailOauthCredentials credentials) {
    throw new IllegalArgumentException("OAuth credentials unsupported by this operation");
  }
}
