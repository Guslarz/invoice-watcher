package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.FolderMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailFolderMessages;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ReceivedDateTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class MailConnectionImplTest {

  private static final String FOLDER = "INBOX";
  private static final long UID_VALIDITY = 7;
  private static final ZonedDateTime START_DATE = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

  @Test
  @DisplayName("MAIL-020: picks up messages with a higher UID than the position, in UID order")
  void returnsMessagesAfterPositionInUidOrder() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        message(10, START_DATE),
        message(11, START_DATE.plusHours(1)),
        message(12, START_DATE.plusHours(2)),
        message(13, START_DATE.plusHours(3)),
        message(14, START_DATE.minusDays(5)),
        message(15, START_DATE.plusHours(5))));

    // when
    MailFolderMessages result = uut.getEmailMessages(query(null, new ScanPosition(UID_VALIDITY, 12)));

    // then
    assertThat(result.uidValidity()).isEqualTo(UID_VALIDITY);
    assertThat(result.messages()).extracting(FolderMessage::uid).containsExactly(13L, 14L, 15L);
    assertThat(result.messages()).extracting(message -> message.message().title())
        .containsExactly("message 13", "message 14", "message 15");
  }

  @Test
  @DisplayName("MAIL-022: an empty folder yields nothing")
  void returnsNothingForEmptyFolder() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages());

    // when
    MailFolderMessages result = uut.getEmailMessages(query(START_DATE, new ScanPosition(UID_VALIDITY, 12)));

    // then
    assertThat(result.messages()).isEmpty();
  }

  @Test
  @DisplayName("MAIL-022: a folder whose last UID equals the position yields nothing")
  void returnsNothingWhenLastUidEqualsPosition() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        message(11, START_DATE),
        message(12, START_DATE.plusHours(1))));

    // when
    MailFolderMessages result = uut.getEmailMessages(query(null, new ScanPosition(UID_VALIDITY, 12)));

    // then
    assertThat(result.messages()).isEmpty();
  }

  @Test
  @DisplayName("MAIL-023: a folder without position picks up messages received at or after the start date")
  void returnsMessagesReceivedSinceStartDateWithoutPosition() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        message(1, START_DATE.minusDays(1)),
        message(2, START_DATE),
        message(3, START_DATE.plusDays(1))));

    // when
    MailFolderMessages result = uut.getEmailMessages(query(START_DATE, null));

    // then
    assertThat(result.messages()).extracting(FolderMessage::uid).containsExactly(2L, 3L);
  }

  @Test
  @DisplayName("MAIL-023: a folder without position and start date picks up all messages")
  void returnsAllMessagesWithoutPositionAndStartDate() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        message(1, START_DATE.minusDays(1)),
        message(2, START_DATE.plusDays(1))));

    // when
    MailFolderMessages result = uut.getEmailMessages(query(null, null));

    // then
    assertThat(result.messages()).extracting(FolderMessage::uid).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("MAIL-024: a changed UIDVALIDITY discards the position and scans from the start date")
  void scansFromStartDateWhenUidValidityChanged() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        message(1, START_DATE.minusDays(1)),
        message(2, START_DATE.plusDays(1))));

    // when
    MailFolderMessages result = uut.getEmailMessages(query(START_DATE, new ScanPosition(UID_VALIDITY - 1, 12)));

    // then
    assertThat(result.uidValidity()).isEqualTo(UID_VALIDITY);
    assertThat(result.messages()).extracting(FolderMessage::uid).containsExactly(2L);
  }

  private static MailQuery query(ZonedDateTime startDate, ScanPosition position) {
    return MailQuery.builder()
        .folder(FOLDER)
        .startDate(startDate)
        .position(position)
        .build();
  }

  /**
   * Store with a single UID folder holding the given messages in the order the server received them.
   * Like IMAP, a UID range {@code n:*} returns the last message when no message has a UID of at least n,
   * and a received-date search matches by day.
   */
  private static Store storeWithMessages(TestMessage... messages) throws Exception {
    List<TestMessage> all = List.of(messages);
    Folder folder = mock(Folder.class, withSettings().extraInterfaces(UIDFolder.class));
    UIDFolder uidFolder = (UIDFolder) folder;
    when(uidFolder.getUIDValidity()).thenReturn(UID_VALIDITY);
    when(uidFolder.getUID(any(Message.class))).thenAnswer(invocation -> all.stream()
        .filter(message -> message.message() == invocation.getArgument(0))
        .findFirst().orElseThrow().uid());
    when(uidFolder.getMessagesByUID(anyLong(), eq(UIDFolder.LASTUID))).thenAnswer(invocation -> {
      long start = invocation.getArgument(0);
      List<TestMessage> range = select(all, message -> message.uid() >= start);
      return toArray(range.isEmpty() && !all.isEmpty() ? List.of(all.getLast()) : range);
    });
    when(folder.getMessages()).thenReturn(toArray(all));
    when(folder.search(any(ReceivedDateTerm.class))).thenAnswer(invocation -> {
      ReceivedDateTerm term = invocation.getArgument(0);
      Date day = Date.from(term.getDate().toInstant().atZone(ZoneId.of("UTC")).toLocalDate()
          .atStartOfDay(ZoneId.of("UTC")).toInstant());
      return toArray(select(all, message -> !message.receivedDate().before(day)));
    });
    Store store = mock(Store.class);
    when(store.getFolder(FOLDER)).thenReturn(folder);
    return store;
  }

  private static List<TestMessage> select(List<TestMessage> messages, Predicate<TestMessage> predicate) {
    return messages.stream().filter(predicate).toList();
  }

  private static Message[] toArray(List<TestMessage> messages) {
    return messages.stream().map(TestMessage::message).toArray(Message[]::new);
  }

  /**
   * Message with the given UID, received and sent at the given date.
   */
  private static TestMessage message(long uid, ZonedDateTime date) {
    try {
      Date received = Date.from(date.toInstant());
      Message message = mock(Message.class);
      when(message.getReceivedDate()).thenReturn(received);
      when(message.getSentDate()).thenReturn(received);
      when(message.getSubject()).thenReturn("message " + uid);
      when(message.getFrom()).thenReturn(new InternetAddress[]{ new InternetAddress("sender@example.com") });
      return new TestMessage(uid, received, message);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record TestMessage(long uid, Date receivedDate, Message message) {
  }
}
