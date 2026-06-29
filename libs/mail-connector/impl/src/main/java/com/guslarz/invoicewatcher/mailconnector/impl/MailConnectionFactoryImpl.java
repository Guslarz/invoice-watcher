package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@RequiredArgsConstructor
public class MailConnectionFactoryImpl implements MailConnectionFactory {

  private final MailConnectorProperties properties;

  @Override
  public MailConnection createConnection() {
    Session javaMailSession = createJavaMailSession(properties);
    return new MailConnectionImpl(properties, javaMailSession);
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
