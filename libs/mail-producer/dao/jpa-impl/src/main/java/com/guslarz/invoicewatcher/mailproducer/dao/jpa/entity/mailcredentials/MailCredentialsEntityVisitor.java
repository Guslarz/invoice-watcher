package com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials;

public interface MailCredentialsEntityVisitor<T> {

  T visit(MailCredentialsEntity entity);

  T visitBasicAuthMailCredentialsEntity(BasicAuthMailCredentialsEntity entity);
}
