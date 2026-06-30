package com.guslarz.invoicewatcher.mailconnector.api;

import java.util.List;

public interface MailConnection extends AutoCloseable {

  List<MailMessage> getEmailMessages(MailQuery query);

  @Override
  void close() throws MailConnectionException;
}
