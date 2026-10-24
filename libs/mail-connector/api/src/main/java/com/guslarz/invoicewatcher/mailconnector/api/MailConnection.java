package com.guslarz.invoicewatcher.mailconnector.api;

public interface MailConnection extends AutoCloseable {

  MailFolderMessages getEmailMessages(MailQuery query);

  @Override
  void close() throws MailConnectionException;
}
