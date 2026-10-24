package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.FolderMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailFolderMessages;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailconnector.impl.util.DateUtil;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class MailConnectionImpl implements MailConnection {

  private final Store store;

  @Override
  public MailFolderMessages getEmailMessages(MailQuery query) {
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

  private MailFolderMessages getEmailMessagesFromFolder(MailQuery query, Folder folder) throws MessagingException {
    if (!(folder instanceof UIDFolder uidFolder)) {
      throw new MessagingException("Folder does not support UIDs: " + query.folder());
    }
    folder.open(Folder.READ_ONLY);
    long uidValidity = uidFolder.getUIDValidity();
    ScanPosition position = query.position();
    List<FolderMessage> messages = position != null && position.uidValidity() == uidValidity
        ? messagesAfter(uidFolder, folder, position.lastUid())
        : messagesReceivedSince(uidFolder, folder, query);
    return MailFolderMessages.builder()
        .uidValidity(uidValidity)
        .messages(messages)
        .build();
  }

  /**
   * Returns the messages with a UID greater than {@code lastUid}. IMAP answers the range
   * {@code lastUid+1:*} with the last message even when it is not newer, so the range is filtered.
   */
  private List<FolderMessage> messagesAfter(UIDFolder uidFolder, Folder folder, long lastUid)
      throws MessagingException {
    Message[] messages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
    return toFolderMessages(uidFolder, folder, messages)
        .filter(message -> message.uid() > lastUid)
        .toList();
  }

  /**
   * Returns the messages the server received at or after the query's start date, or all messages
   * without a start date. IMAP searches by day only, so the search result is filtered to the second.
   */
  private List<FolderMessage> messagesReceivedSince(UIDFolder uidFolder, Folder folder, MailQuery query)
      throws MessagingException {
    if (query.startDate() == null) {
      return toFolderMessages(uidFolder, folder, folder.getMessages()).toList();
    }
    Date startDate = DateUtil.toDate(query.startDate());
    Message[] messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, startDate));
    return toFolderMessages(uidFolder, folder, Stream.of(messages)
        .filter(message -> !receivedDate(message).before(startDate))
        .toArray(Message[]::new))
        .toList();
  }

  private Stream<FolderMessage> toFolderMessages(UIDFolder uidFolder, Folder folder, Message[] messages)
      throws MessagingException {
    FetchProfile fetchProfile = new FetchProfile();
    fetchProfile.add(UIDFolder.FetchProfileItem.UID);
    fetchProfile.add(FetchProfile.Item.ENVELOPE);
    folder.fetch(messages, fetchProfile);
    return Stream.of(messages)
        .map(message -> FolderMessage.builder()
            .uid(uid(uidFolder, message))
            .message(toMessage(message))
            .build())
        .sorted(Comparator.comparingLong(FolderMessage::uid));
  }

  private long uid(UIDFolder uidFolder, Message message) {
    try {
      return uidFolder.getUID(message);
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
  }

  private Date receivedDate(Message message) {
    try {
      return message.getReceivedDate();
    } catch (MessagingException e) {
      throw new MailConnectionException(e);
    }
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
