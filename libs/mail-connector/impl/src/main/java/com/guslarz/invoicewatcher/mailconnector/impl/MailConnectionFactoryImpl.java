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

  private final MailConnectorImapProperties imapProperties;

  @Override
  public MailConnection createConnection(MailConnectionProperties properties) {
    Session session = Session.getInstance(sessionProperties());
    try {
      Store store = session.getStore(IMAP_PROTOCOL);
      new ConnectVisitor(store, properties).visit(properties.mailCredentials());
      return new MailConnectionImpl(store);
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }

  /**
   * Session settings stated explicitly, independent of the defaults of the mail library. No write
   * timeout: it costs a thread per connection, and the read timeout covers a server that stops
   * answering.
   */
  Properties sessionProperties() {
    Properties sessionProperties = new Properties();
    sessionProperties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
    sessionProperties.setProperty("mail.imaps.connectiontimeout",
        String.valueOf(imapProperties.connectTimeout().toMillis()));
    sessionProperties.setProperty("mail.imaps.timeout",
        String.valueOf(imapProperties.readTimeout().toMillis()));
    return sessionProperties;
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
