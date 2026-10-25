package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.mailconnector.api.FolderMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailFolderMessages;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.secret.api.Secret;
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

  @Test
  @DisplayName("MAIL-030: a message without sender and date is published as read and the position moves past it")
  void publishesMessageWithoutSenderAndDate() {
    // given
    FolderConfig inbox = folder("INBOX", null);
    givenUserWithFolders(inbox);
    MailFolderMessages folderMessages = messages(UID_VALIDITY, 1, 3);
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(MailFolderMessages.builder()
        .uidValidity(UID_VALIDITY)
        .messages(List.of(
            folderMessages.messages().get(0),
            FolderMessage.builder().uid(2).message(MailMessage.builder().title("Invoice 2").build()).build(),
            folderMessages.messages().get(1)))
        .build());

    // when
    uut.execute();

    // then
    ArgumentCaptor<Event> events = ArgumentCaptor.forClass(Event.class);
    verify(eventProducer, times(3)).send(any(), events.capture());
    MailMessage second = (MailMessage) events.getAllValues().get(1).value();
    assertThat(second.sender()).isNull();
    assertThat(second.date()).isNull();
    verify(folderConfigDaoService).updateScanPosition(inbox, new ScanPosition(UID_VALIDITY, 3));
  }

  @Test
  @DisplayName("MAIL-040: a mailbox that cannot be connected to is logged and the next mailbox is scanned")
  void continuesWithNextMailboxWhenConnectingFails(CapturedOutput output) {
    // given
    MailConnection secondConnection = mock(MailConnection.class);
    when(userDaoService.findAll()).thenReturn(List.of(User.builder()
        .name("jan@example.com")
        .scanConfigs(List.of(scanConfig("imap.broken.example", folder("INBOX", null)),
            scanConfig("imap.example.com", folder("INBOX", null))))
        .build()));
    when(mailConnectionFactory.createConnection(any()))
        .thenThrow(new MailConnectionException(new RuntimeException("Wrong password")))
        .thenReturn(secondConnection);
    when(secondConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY, 1));

    // when
    uut.execute();

    // then
    verify(eventProducer).send(any(), any());
    assertThat(output).contains("Error scanning mailbox imap.broken.example of user jan@example.com");
  }

  @Test
  @DisplayName("MAIL-042: a failing user is logged and the next user is scanned")
  void continuesWithNextUserWhenUserFails(CapturedOutput output) {
    // given
    User broken = mock(User.class);
    when(broken.name()).thenReturn("broken@example.com");
    when(broken.scanConfigs()).thenThrow(new IllegalArgumentException("Unknown credentials type: oauth"));
    when(userDaoService.findAll()).thenReturn(List.of(broken, user("jan@example.com", folder("INBOX", null))));
    when(mailConnectionFactory.createConnection(any())).thenReturn(mailConnection);
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY, 1));

    // when
    uut.execute();

    // then
    verify(eventProducer).send(any(), any());
    assertThat(output).contains("Error processing user broken@example.com", "Unknown credentials type: oauth");
  }

  @Test
  @DisplayName("MAIL-043: a failed folder is logged once, with user, host and folder and without credentials")
  void logsFolderFailureOnceWithoutCredentials(CapturedOutput output) {
    // given
    Secret password = mock(Secret.class);
    when(password.getEncrypted()).thenReturn("encrypted-s3cret");
    when(password.getDecrypted()).thenReturn("plain-s3cret");
    when(password.toString()).thenReturn("plain-s3cret");
    when(userDaoService.findAll()).thenReturn(List.of(User.builder()
        .name("jan@example.com")
        .scanConfigs(List.of(MailScanConfig.builder()
            .server(MailServer.builder().host("imap.example.com").port(993).build())
            .credentials(BasicAuthMailCredentials.builder().username("jan.login").password(password).build())
            .folders(List.of(folder("Invoices", null)))
            .build()))
        .build()));
    when(mailConnectionFactory.createConnection(any())).thenReturn(mailConnection);
    when(mailConnection.getEmailMessages(any(MailQuery.class)))
        .thenThrow(new MailConnectionException(new RuntimeException("Folder not found")));

    // when
    uut.execute();

    // then
    List<String> errorLines = output.getAll().lines().filter(line -> line.contains("ERROR")).toList();
    assertThat(errorLines).singleElement().asString()
        .contains("jan@example.com", "imap.example.com", "Invoices");
    assertThat(output).doesNotContain("jan.login", "plain-s3cret", "encrypted-s3cret");
  }

  @Test
  @DisplayName("MAIL-043: a failure closing the connection after a scan is logged as a warning only")
  void logsCloseFailureAsWarning(CapturedOutput output) throws Exception {
    // given
    FolderConfig inbox = folder("INBOX", null);
    givenUserWithFolders(inbox);
    when(mailConnection.getEmailMessages(any(MailQuery.class))).thenReturn(messages(UID_VALIDITY, 1));
    doThrow(new MailConnectionException(new RuntimeException("Connection reset"))).when(mailConnection).close();

    // when
    uut.execute();

    // then
    verify(folderConfigDaoService).updateScanPosition(inbox, new ScanPosition(UID_VALIDITY, 1));
    assertThat(output).contains("WARN", "Error closing connection to mailbox imap.example.com of user jan@example.com");
    assertThat(output).doesNotContain("ERROR");
  }

  private MailQuery capturedQuery() {
    ArgumentCaptor<MailQuery> query = ArgumentCaptor.forClass(MailQuery.class);
    verify(mailConnection).getEmailMessages(query.capture());
    return query.getValue();
  }

  private void givenUserWithFolders(FolderConfig... folders) {
    when(userDaoService.findAll()).thenReturn(List.of(user("jan@example.com", folders)));
    when(mailConnectionFactory.createConnection(any())).thenReturn(mailConnection);
  }

  private static User user(String name, FolderConfig... folders) {
    return User.builder()
        .name(name)
        .scanConfigs(List.of(scanConfig("imap.example.com", folders)))
        .build();
  }

  private static MailScanConfig scanConfig(String host, FolderConfig... folders) {
    return MailScanConfig.builder()
        .server(MailServer.builder().host(host).port(993).build())
        .folders(List.of(folders))
        .build();
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
