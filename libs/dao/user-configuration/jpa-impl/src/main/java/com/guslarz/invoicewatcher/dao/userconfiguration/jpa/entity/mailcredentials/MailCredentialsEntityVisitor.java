package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials;

public interface MailCredentialsEntityVisitor<T> {

  T visit(MailCredentialsEntity entity);

  T visitBasicAuthMailCredentialsEntity(BasicAuthMailCredentialsEntity entity);

  T visitGmailOauthCredentialsEntity(GmailOauthCredentialsEntity entity);
}
