package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailconnector.impl.util.DateUtil;
import com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch.BinarySearchConfig;
import com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch.BinarySearchEngine;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class MailConnectionImpl implements MailConnection {

  private final Store store;

  @Override
  public List<MailMessage> getEmailMessages(MailQuery query) {
    try (Folder folder = store.getFolder(query.folder())) {
      return getEmailMessagesFromFolder(query, folder);
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }

  @Override
  public void close() throws MailConnectionException {
    try {
      store.close();
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }

  private List<MailMessage> getEmailMessagesFromFolder(MailQuery query, Folder folder) throws MessagingException {
    folder.open(Folder.READ_ONLY);
    int messageCount = folder.getMessageCount();
    int startIndex = findFirstIndexMatchingQuery(query, folder, messageCount);
    Message[] messages = folder.getMessages(startIndex, messageCount);
    return Stream.of(messages)
        .map(this::toMessage)
        .toList();
  }

  private int findFirstIndexMatchingQuery(MailQuery query, Folder folder, int messageCount) {
    BinarySearchEngine engine = new BinarySearchEngine();
    Date dateFrom = DateUtil.toDate(query.dateFrom());
    BinarySearchConfig config = BinarySearchConfig.builder()
        .itemCount(messageCount)
        .searchPredicate(index -> {
          try {
            return !dateFrom.after(folder.getMessage(index).getSentDate());
          } catch (MessagingException e) {
            throw new MailConnectionException(e);
          }
        })
        .build();
    return engine.getIndexEqualOrGreaterThan(config);
  }

  private MailMessage toMessage(Message message) {
    try {
      return MailMessage.builder()
          .sender(message.getFrom()[0].toString())
          .title(message.getSubject())
          .date(DateUtil.toZonedDateTime(message.getSentDate()))
          .build();
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }
}
