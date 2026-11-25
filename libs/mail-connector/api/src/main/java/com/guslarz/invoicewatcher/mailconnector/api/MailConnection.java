package com.guslarz.invoicewatcher.mailconnector.api;

import java.util.function.BiConsumer;

public interface MailConnection extends AutoCloseable {

  MailFolderMessages getEmailMessages(MailQuery query);

  /** Processes messages while their MIME sources remain available inside the open folder. */
  default long forEachEmailMessage(MailQuery query, BiConsumer<Long, FolderMessage> consumer) {
    MailFolderMessages messages = getEmailMessages(query);
    for (FolderMessage message : messages.messages()) {
      consumer.accept(messages.uidValidity(), message);
    }
    return messages.uidValidity();
  }

  @Override
  void close() throws MailConnectionException;
}
