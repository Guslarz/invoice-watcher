package com.guslarz.invoicewatcher.mailconnector.api;

public interface MailConnectionFactory {

  MailConnection createConnection(MailConnectionProperties properties);
}
