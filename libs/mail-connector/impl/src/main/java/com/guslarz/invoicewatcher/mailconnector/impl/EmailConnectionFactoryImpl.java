package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.EmailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.EmailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.EmailConnectionProperties;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import lombok.RequiredArgsConstructor;

import java.util.Properties;

public class EmailConnectionFactoryImpl implements EmailConnectionFactory {

  @Override
  public EmailConnection createConnection(EmailConnectionProperties properties) {
    Session javaMailSession = createJavaMailSession(properties);
    return new EmailConnectionImpl(properties, javaMailSession);
  }

  private Session createJavaMailSession(EmailConnectionProperties properties) {
    Properties javaMailProperties = createJavaMailProperties(properties);
    return Session.getInstance(javaMailProperties, new PasswordAuthenticator(properties));
  }

  private Properties createJavaMailProperties(EmailConnectionProperties properties) {
    Properties props = new Properties();
    props.put("mail.imap.host", properties.host());
    props.put("mail.imap.port", properties.port());
    props.put("mail.imap.auth", Boolean.TRUE.toString());
    props.put("mail.imap.starttls.enable", Boolean.TRUE.toString());
    return props;
  }

  @RequiredArgsConstructor
  private static class PasswordAuthenticator extends Authenticator {

    private final EmailConnectionProperties properties;

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(properties.username(), properties.password());
    }
  }
}
