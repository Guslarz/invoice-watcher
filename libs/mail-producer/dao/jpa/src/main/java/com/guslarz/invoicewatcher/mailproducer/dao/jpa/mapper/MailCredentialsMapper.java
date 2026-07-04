package com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BaseMailCredentialsVisitor;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials.BasicAuthMailCredentialsEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials.MailCredentialsEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials.MailCredentialsEntityVisitor;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailCredentialsMapper extends BaseMailCredentialsVisitor<MailCredentialsEntity> implements
    MailCredentialsEntityVisitor<MailCredentials> {

  private final SecretFactory secretFactory;

  @Override
  public MailCredentialsEntity visitBasicMailCredentials(BasicAuthMailCredentials mailCredentials) {
    return BasicAuthMailCredentialsEntity.builder()
        .username(mailCredentials.username())
        .password(mailCredentials.password().getEncrypted())
        .build();
  }

  @Override
  public MailCredentials visit(MailCredentialsEntity entity) {
    return entity.accept(this);
  }

  @Override
  public MailCredentials visitBasicAuthMailCredentialsEntity(BasicAuthMailCredentialsEntity entity) {
    return BasicAuthMailCredentials.builder()
        .username(entity.username())
        .password(secretFactory.fromEncrypted(entity.password()))
        .build();
  }
}
