package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

public abstract class BaseMailCredentialsVisitor<T> implements MailCredentialsVisitor<T> {

  public T visit(MailCredentials mailCredentials) {
    return mailCredentials.accept(this);
  }
}
