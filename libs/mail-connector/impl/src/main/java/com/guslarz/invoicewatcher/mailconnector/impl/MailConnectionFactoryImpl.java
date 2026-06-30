package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@RequiredArgsConstructor
public class MailConnectionFactoryImpl implements MailConnectionFactory {

  private static final String IMAP_PROTOCOL = "imaps";

  private final MailConnectorProperties properties;

  @Override
  public MailConnection createConnection() {
    Session session = createJavaMailSession(properties);
    try {
      Store store = session.getStore(IMAP_PROTOCOL);
      store.connect(properties.host(), properties.username(), properties.password());
      return new MailConnectionImpl(properties, session, store);
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }

  private Session createJavaMailSession(MailConnectorProperties properties) {
    Properties javaMailProperties = createJavaMailProperties(properties);
    return Session.getInstance(javaMailProperties, new PasswordAuthenticator(properties));
  }

  private Properties createJavaMailProperties(MailConnectorProperties properties) {
    Properties props = new Properties();
    props.put("mail.imap.host", properties.host());
    props.put("mail.imap.port", properties.port());
    props.put("mail.imap.auth", Boolean.TRUE.toString());
    props.put("mail.imap.starttls.enable", Boolean.TRUE.toString());
    return props;
  }

  @RequiredArgsConstructor
  private static class PasswordAuthenticator extends Authenticator {

    private final MailConnectorProperties properties;

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(properties.username(), properties.password());
    }
  }
}
