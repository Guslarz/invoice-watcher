package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailProducerTaskImpl implements MailProducerTask {

  private final MailConnectionFactory mailConnectionFactory;

  @Override
  public void execute() {
    try (MailConnection mailConnection = mailConnectionFactory.createConnection()) {
      processEmailMessages(mailConnection.getEmailMessages(query()));
    }
  }

  private MailQuery query() {
    return MailQuery.builder()
        .folder("dupa")
        .dateFrom(ZonedDateTime.of(2026, 6, 27, 0, 0, 0, 0, ZonedDateTime.now().getZone()))
        .build();
  }

  private void processEmailMessages(List<MailMessage> emailMessages) {
    for (MailMessage message : emailMessages) {
      log.info("Processing email message: {}", message);
    }
  }
}
