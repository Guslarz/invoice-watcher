package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.mailconnector.api.FolderMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailFolderMessages;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class MailProducerTaskImplTest {

  private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-22T10:00:00Z");
  private static final long UID_VALIDITY = 7;

  private final UserDaoService userDaoService = mock(UserDaoService.class);
  private final FolderConfigDaoService folderConfigDaoService = mock(FolderConfigDaoService.class);
  private final MailConnectionFactory mailConnectionFactory = mock(MailConnectionFactory.class);
  private final MailConnection mailConnection = mock(MailConnection.class);
  private final EventProducer eventProducer = mock(EventProducer.class);
  private final MailProducerTaskImpl uut = new MailProducerTaskImpl(
      userDaoService,
      folderConfigDaoService,
      mailConnectionFactory,
      new MailProcessorService(new MailProducerTaskProperties("all-mails"), eventProducer)
  );

  @Test
  @DisplayName("EVENT-011: saves the position once, at the last message, after all events are confirmed")
  void savesPositionOnceWhenAllEventsConfirmed() {
    // given
    FolderConfig inbox = folder("INBOX", null);
    givenUserWithFolders(inbox);
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY, 13, 14));

    // when
    uut.execute();

    // then
    verify(eventProducer, times(2)).send(any(), any());
    verify(folderConfigDaoService).updateScanPosition(inbox, new ScanPosition(UID_VALIDITY, 14));
  }

  @Test
  @DisplayName("MAIL-021: the next scan asks for the messages after the saved position")
  void queriesMessagesAfterSavedPosition() {
    // given
    givenUserWithFolders(folder("INBOX", new ScanPosition(UID_VALIDITY, 15)));
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY));

    // when
    uut.execute();

    // then
    assertThat(capturedQuery().position()).isEqualTo(new ScanPosition(UID_VALIDITY, 15));
    verify(eventProducer, never()).send(any(), any());
  }

  @Test
  @DisplayName("MAIL-022: a folder without new messages publishes nothing and keeps its position")
  void keepsPositionWhenNoNewMessages(CapturedOutput output) {
    // given
    givenUserWithFolders(folder("INBOX", new ScanPosition(UID_VALIDITY, 15)));
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY));

    // when
    uut.execute();

    // then
    verify(eventProducer, never()).send(any(), any());
    verify(folderConfigDaoService, never()).updateScanPosition(any(), any());
    assertThat(output).doesNotContain("Error");
  }

  @Test
  @DisplayName("MAIL-023: a folder without position is queried by its start date")
  void queriesByStartDateWithoutPosition() {
    // given
    givenUserWithFolders(folder("INBOX", null));
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY));

    // when
    uut.execute();

    // then
    MailQuery query = capturedQuery();
    assertThat(query.position()).isNull();
    assertThat(query.startDate()).isEqualTo(NOW.minusDays(10));
  }

  @Test
  @DisplayName("MAIL-024: the position is saved with the UIDVALIDITY the server reports")
  void savesPositionWithCurrentUidValidity() {
    // given
    FolderConfig inbox = folder("INBOX", new ScanPosition(UID_VALIDITY, 15));
    givenUserWithFolders(inbox);
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY + 1, 1, 2));

    // when
    uut.execute();

    // then
    verify(folderConfigDaoService).updateScanPosition(inbox, new ScanPosition(UID_VALIDITY + 1, 2));
  }

  @Test
  @DisplayName("EVENT-012: saves the position at the last confirmed event and skips remaining folders when an "
      + "event is not confirmed")
  void savesPositionAtLastConfirmedEventWhenEventNotConfirmed(CapturedOutput output) {
    // given
    FolderConfig inbox = folder("INBOX", null);
    givenUserWithFolders(inbox, folder("Invoices", null));
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY, 13, 14, 15));
    doNothing()
        .doThrow(new EventDeliveryException("Event not delivered", new RuntimeException()))
        .when(eventProducer).send(any(), any());

    // when
    uut.execute();

    // then
    verify(eventProducer, times(2)).send(any(), any());
    verify(folderConfigDaoService).updateScanPosition(inbox, new ScanPosition(UID_VALIDITY, 13));
    verify(mailConnection, times(1)).getEmailMessages(any(MailQuery.class));
    assertThat(output).contains("Error scanning folder", "Event not delivered");
  }

  @Test
  @DisplayName("EVENT-012: a folder whose first event is not confirmed keeps its position")
  void keepsPositionWhenFirstEventNotConfirmed() {
    // given
    givenUserWithFolders(folder("INBOX", new ScanPosition(UID_VALIDITY, 12)));
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY, 13, 14));
    doThrow(new EventDeliveryException("Event not delivered", new RuntimeException()))
        .when(eventProducer).send(any(), any());

    // when
    uut.execute();

    // then
    verify(folderConfigDaoService, never()).updateScanPosition(any(), any());
  }

  private MailQuery capturedQuery() {
    ArgumentCaptor<MailQuery> query = ArgumentCaptor.forClass(MailQuery.class);
    verify(mailConnection).getEmailMessages(query.capture());
    return query.getValue();
  }

  private void givenUserWithFolders(FolderConfig... folders) {
    when(userDaoService.findAll()).thenReturn(List.of(User.builder()
        .name("jan@example.com")
        .scanConfigs(List.of(MailScanConfig.builder()
            .server(MailServer.builder().host("imap.example.com").port(993).build())
            .folders(List.of(folders))
            .build()))
        .build()));
    when(mailConnectionFactory.createConnection(any())).thenReturn(mailConnection);
  }

  private static FolderConfig folder(String name, ScanPosition position) {
    return FolderConfig.builder()
        .name(name)
        .startDate(NOW.minusDays(10))
        .uidValidity(position != null ? position.uidValidity() : null)
        .lastUid(position != null ? position.lastUid() : null)
        .build();
  }

  private static MailFolderMessages messages(long uidValidity, long... uids) {
    return MailFolderMessages.builder()
        .uidValidity(uidValidity)
        .messages(LongStream.of(uids)
            .mapToObj(uid -> FolderMessage.builder()
                .uid(uid)
                .message(MailMessage.builder()
                    .sender("billing@shop.example")
                    .title("Invoice " + uid)
                    .date(NOW)
                    .build())
                .build())
            .toList())
        .build();
  }
}
