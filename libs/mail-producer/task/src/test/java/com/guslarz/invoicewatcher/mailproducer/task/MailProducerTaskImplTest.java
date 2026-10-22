package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.eventproducer.api.EventDeliveryException;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class MailProducerTaskImplTest {

  private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-22T10:00:00Z");

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
  @DisplayName("EVENT-011: moves the scan timestamp forward after all events are confirmed")
  void movesScanTimestampWhenAllEventsConfirmed() {
    // given
    FolderConfig inbox = folder("INBOX");
    givenUserWithFolders(inbox);
    when(mailConnection.getEmailMessages(any(MailQuery.class)))
        .thenReturn(List.of(message(NOW.minusHours(1)), message(NOW)));

    // when
    uut.execute();

    // then
    verify(eventProducer, times(2)).send(any(), any());
    verify(folderConfigDaoService).updateScanTimestamp(eq(inbox), any());
  }

  @Test
  @DisplayName("EVENT-012: keeps the scan timestamp and skips remaining folders when an event is not "
      + "confirmed")
  void keepsScanTimestampWhenEventNotConfirmed(CapturedOutput output) {
    // given
    givenUserWithFolders(folder("INBOX"), folder("Invoices"));
    when(mailConnection.getEmailMessages(any(MailQuery.class)))
        .thenReturn(List.of(message(NOW.minusHours(2)), message(NOW.minusHours(1)), message(NOW)));
    doNothing()
        .doThrow(new EventDeliveryException("Event not delivered", new RuntimeException()))
        .when(eventProducer).send(any(), any());

    // when
    uut.execute();

    // then
    verify(eventProducer, times(2)).send(any(), any());
    verify(folderConfigDaoService, never()).updateScanTimestamp(any(), any());
    verify(mailConnection, times(1)).getEmailMessages(any(MailQuery.class));
    assertThat(output).contains("Error scanning folder", "Event not delivered");
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

  private static FolderConfig folder(String name) {
    return FolderConfig.builder()
        .name(name)
        .scanTimestamp(NOW.minusDays(1))
        .build();
  }

  private static MailMessage message(ZonedDateTime date) {
    return MailMessage.builder()
        .sender("billing@shop.example")
        .title("Invoice 10/2026")
        .date(date)
        .build();
  }
}
