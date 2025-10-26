package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.*;
import com.guslarz.invoicewatcher.mailconnector.impl.util.DateUtil;
import com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch.BinarySearchConfig;
import com.guslarz.invoicewatcher.mailconnector.impl.util.binarysearch.BinarySearchEngine;
import jakarta.mail.*;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class EmailConnectionImpl implements EmailConnection {

  private static final String IMAP_PROTOCOL = "imaps";

  private final EmailConnectionProperties properties;
  private final Session session;

  @Override
  public List<EmailMessage> getEmailMessages(EmailQuery query) {
    try (Store store = session.getStore(IMAP_PROTOCOL)) {
      store.connect(properties.host(), properties.username(), properties.password());
      return getEmailMessages(query, store);
    } catch (MessagingException e) {
      throw new EmailConnectionException(e);
    }
  }

  private List<EmailMessage> getEmailMessages(EmailQuery query, Store store) {
    try (Folder folder = store.getFolder(query.folder())) {
      folder.open(Folder.READ_ONLY);
      return getEmailMessages(query, folder);
    } catch (MessagingException e) {
      throw new EmailConnectionException(e);
    }
  }

  private List<EmailMessage> getEmailMessages(EmailQuery query, Folder folder) throws MessagingException {
    int messageCount = folder.getMessageCount();
    int startIndex = findFirstIndexMatchingQuery(query, folder, messageCount);
    Message[] messages = folder.getMessages(startIndex, messageCount);
    return Stream.of(messages)
        .map(this::toMessage)
        .toList();
  }

  private int findFirstIndexMatchingQuery(EmailQuery query, Folder folder, int messageCount) {
    BinarySearchEngine engine = new BinarySearchEngine();
    Date dateFrom = DateUtil.toDate(query.dateFrom());
    BinarySearchConfig config = BinarySearchConfig.builder()
        .itemCount(messageCount)
        .searchPredicate(index -> {
          try {
            return !dateFrom.after(folder.getMessage(index).getSentDate());
          } catch (MessagingException e) {
            throw new EmailConnectionException(e);
          }
        })
        .build();
    return engine.getIndexEqualOrGreaterThan(config);
  }

  private EmailMessage toMessage(Message message) {
    try {
      return EmailMessage.builder()
          .sender(message.getFrom()[0].toString())
          .title(message.getSubject())
          .date(DateUtil.toZonedDateTime(message.getSentDate()))
          .build();
    } catch (MessagingException e) {
      throw new EmailConnectionException(e);
    }
  }
}
