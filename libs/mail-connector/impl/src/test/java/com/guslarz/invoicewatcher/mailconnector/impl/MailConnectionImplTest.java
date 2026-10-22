package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailConnectionImplTest {

  private static final String FOLDER = "INBOX";
  private static final ZonedDateTime SCAN_TIMESTAMP = ZonedDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneId.of("UTC"));

  @Test
  void shouldReturnNothingForEmptyFolder() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages());

    // when
    List<MailMessage> result = uut.getEmailMessages(query());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("MAIL-022: folder without new messages yields nothing")
  void shouldReturnNothingWhenAllMessagesAreOlderThanScanTimestamp() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        SCAN_TIMESTAMP.minusDays(3),
        SCAN_TIMESTAMP.minusDays(2),
        SCAN_TIMESTAMP.minusSeconds(1)));

    // when
    List<MailMessage> result = uut.getEmailMessages(query());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnAllMessagesWhenAllAreNewerThanScanTimestamp() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        SCAN_TIMESTAMP.plusSeconds(1),
        SCAN_TIMESTAMP.plusDays(1),
        SCAN_TIMESTAMP.plusDays(2)));

    // when
    List<MailMessage> result = uut.getEmailMessages(query());

    // then
    assertThat(result).extracting(MailMessage::title)
        .containsExactly("message 1", "message 2", "message 3");
  }

  @Test
  @DisplayName("MAIL-020: messages sent at or after the scan timestamp are picked up")
  void shouldReturnMessagesSentAtOrAfterScanTimestamp() throws Exception {
    // given
    MailConnectionImpl uut = new MailConnectionImpl(storeWithMessages(
        SCAN_TIMESTAMP.minusDays(2),
        SCAN_TIMESTAMP.minusSeconds(1),
        SCAN_TIMESTAMP,
        SCAN_TIMESTAMP.plusDays(1)));

    // when
    List<MailMessage> result = uut.getEmailMessages(query());

    // then
    assertThat(result).extracting(MailMessage::title)
        .containsExactly("message 3", "message 4");
  }

  private static MailQuery query() {
    return MailQuery.builder()
        .folder(FOLDER)
        .dateFrom(SCAN_TIMESTAMP)
        .build();
  }

  /**
   * Store with a single folder holding messages sent at the given dates, addressed by IMAP message
   * numbers {@code 1..count}.
   */
  private static Store storeWithMessages(ZonedDateTime... sentDates) throws Exception {
    List<Message> messages = IntStream.range(0, sentDates.length)
        .mapToObj(index -> message(index + 1, sentDates[index]))
        .toList();
    Folder folder = mock(Folder.class);
    when(folder.getMessageCount()).thenReturn(messages.size());
    when(folder.getMessage(anyInt())).thenAnswer(invocation -> {
      int number = invocation.getArgument(0);
      return messages.get(toListIndex(number, messages.size()));
    });
    when(folder.getMessages(anyInt(), anyInt())).thenAnswer(invocation -> {
      int start = invocation.getArgument(0);
      int end = invocation.getArgument(1);
      return messages.subList(toListIndex(start, messages.size()), toListIndex(end, messages.size()) + 1)
          .toArray(Message[]::new);
    });
    Store store = mock(Store.class);
    when(store.getFolder(FOLDER)).thenReturn(folder);
    return store;
  }

  private static int toListIndex(int messageNumber, int messageCount) {
    if (messageNumber < 1 || messageNumber > messageCount) {
      throw new IndexOutOfBoundsException("message number out of range: " + messageNumber);
    }
    return messageNumber - 1;
  }

  private static Message message(int number, ZonedDateTime sentDate) {
    try {
      Message message = mock(Message.class);
      when(message.getSentDate()).thenReturn(Date.from(sentDate.toInstant()));
      when(message.getSubject()).thenReturn("message " + number);
      when(message.getFrom()).thenReturn(new InternetAddress[]{ new InternetAddress("sender@example.com") });
      return message;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
