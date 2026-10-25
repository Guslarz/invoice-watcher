package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionProperties;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BaseMailCredentialsVisitor;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@RequiredArgsConstructor
public class MailConnectionFactoryImpl implements MailConnectionFactory {

  private static final String IMAP_PROTOCOL = "imaps";

  @Override
  public MailConnection createConnection(MailConnectionProperties properties) {
    Session session = Session.getInstance(new Properties());
    try {
      Store store = session.getStore(IMAP_PROTOCOL);
      new ConnectVisitor(store, properties).visit(properties.mailCredentials());
      return new MailConnectionImpl(store);
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }

  @RequiredArgsConstructor
  private static class ConnectVisitor extends BaseMailCredentialsVisitor<Void> {

    private final Store store;
    private final MailConnectionProperties properties;

    @Override
    @SneakyThrows
    public Void visitBasicMailCredentials(BasicAuthMailCredentials mailCredentials) {
      store.connect(
          properties.host(),
          properties.port(),
          mailCredentials.username(),
          mailCredentials.password().getDecrypted()
      );
      return null;
    }
  }
}
