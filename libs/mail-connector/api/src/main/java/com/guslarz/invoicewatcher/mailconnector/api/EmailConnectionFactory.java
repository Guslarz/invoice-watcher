package com.guslarz.invoicewatcher.mailconnector.api;

public interface EmailConnectionFactory {

  EmailConnection createConnection(EmailConnectionProperties properties);
}
