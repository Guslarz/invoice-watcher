package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.FolderMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailFolderMessages;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailconnector.impl.service.JakartaAttachmentPart;
import com.guslarz.invoicewatcher.mailconnector.impl.util.DateUtil;
import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

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
  public long forEachEmailMessage(MailQuery query, BiConsumer<Long, FolderMessage> consumer) {
    try (Folder folder = store.getFolder(query.folder())) {
      return withMessages(query, folder, consumer, true);
    } catch (MessagingException exception) {
      throw new MailConnectionException(exception);
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

  private MailFolderMessages getEmailMessagesFromFolder(MailQuery query, Folder folder)
      throws MessagingException {
    var messages = new ArrayList<FolderMessage>();
    long validity = withMessages(query, folder, (ignored, message) -> messages.add(message), false);
    return new MailFolderMessages(validity, messages);
  }

  private long withMessages(
      MailQuery query,
      Folder folder,
      BiConsumer<Long, FolderMessage> consumer,
      boolean liveAttachments)
      throws MessagingException {
    if (!(folder instanceof UIDFolder uidFolder)) {
      throw new MessagingException("Folder does not support UIDs");
    }
    folder.open(Folder.READ_ONLY);
    long validity = uidFolder.getUIDValidity();
    ScanPosition position = query.position();
    Stream<FolderMessage> messages =
        position != null && position.uidValidity() == validity
            ? messagesAfter(uidFolder, folder, position.lastUid(), liveAttachments)
            : messagesReceivedSince(uidFolder, folder, query, liveAttachments);
    messages.forEach(message -> consumer.accept(validity, message));
    return validity;
  }

  /**
   * Returns the messages with a UID greater than {@code lastUid}. IMAP answers the range {@code
   * lastUid+1:*} with the last message even when it is not newer, so the range is filtered.
   */
  private Stream<FolderMessage> messagesAfter(
      UIDFolder uidFolder, Folder folder, long lastUid, boolean liveAttachments)
      throws MessagingException {
    Message[] messages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
    Message[] newMessages =
        Stream.of(messages)
            .filter(message -> uid(uidFolder, message) > lastUid)
            .toArray(Message[]::new);
    return toFolderMessages(uidFolder, folder, newMessages, liveAttachments);
  }

  /**
   * Returns the messages the server received at or after the query's start date, or all messages
   * without a start date. IMAP searches by day only, so the search result is filtered to the
   * second; a message without a received date is kept, as the server's search already selected it.
   */
  private Stream<FolderMessage> messagesReceivedSince(
      UIDFolder uidFolder, Folder folder, MailQuery query, boolean liveAttachments)
      throws MessagingException {
    if (query.startDate() == null) {
      return toFolderMessages(uidFolder, folder, folder.getMessages(), liveAttachments);
    }
    Date startDate = DateUtil.toDate(query.startDate());
    Message[] messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, startDate));
    return toFolderMessages(
        uidFolder,
        folder,
        Stream.of(messages)
            .filter(message ->
                receivedDate(message) == null || !receivedDate(message).before(startDate))
            .toArray(Message[]::new),
        liveAttachments);
  }

  private Stream<FolderMessage> toFolderMessages(
      UIDFolder uidFolder, Folder folder, Message[] messages, boolean liveAttachments)
      throws MessagingException {
    FetchProfile fetchProfile = new FetchProfile();
    fetchProfile.add(UIDFolder.FetchProfileItem.UID);
    fetchProfile.add(FetchProfile.Item.ENVELOPE);
    folder.fetch(messages, fetchProfile);
    return Stream.of(messages)
        .sorted(Comparator.comparingLong(message -> uid(uidFolder, message)))
        .map(message ->
            FolderMessage.builder()
                .uid(uid(uidFolder, message))
                .message(toMessage(message, liveAttachments))
                .build());
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

  private MailMessage toMessage(Message message, boolean liveAttachments) {
    try {
      MimeBodyReader.Body body = new MimeBodyReader().read(message);
      return MailMessage.builder()
          .sender(sender(message))
          .title(message.getSubject())
          .date(date(message))
          .content(body.content())
          .contentType(body.contentType())
          .attachmentRoot(liveAttachments ? new JakartaAttachmentPart(message) : null)
          .build();
    } catch (MessagingException | IOException e) {
      throw new MailConnectionException(e);
    }
  }

  /** Returns the first {@code From} address, or {@code null} when the message has none. */
  private String sender(Message message) throws MessagingException {
    Address[] from = message.getFrom();
    return from == null || from.length == 0 ? null : from[0].toString();
  }

  /**
   * Returns the sent date, or the date the server received the message when it has no valid {@code
   * Date} header, or {@code null} when neither is known.
   */
  private ZonedDateTime date(Message message) throws MessagingException {
    Date date = message.getSentDate() != null ? message.getSentDate() : message.getReceivedDate();
    return date == null ? null : DateUtil.toZonedDateTime(date);
  }
}
