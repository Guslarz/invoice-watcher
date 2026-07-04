package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionProperties;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
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

  private final UserDaoService userDaoService;
  private final MailConnectionFactory mailConnectionFactory;

  @Override
  public void execute() {
    userDaoService.findAll().forEach(this::processUser);
  }

  private void processUser(User user) {
    try (MailConnection mailConnection = mailConnectionFactory.createConnection(connectionProperties(user))) {
      processEmailMessages(mailConnection.getEmailMessages(query()));
    }
  }

  private MailConnectionProperties connectionProperties(User user) {
    return MailConnectionProperties.builder()
        .host(user.mailServer().host())
        .port(user.mailServer().port())
        .mailCredentials(user.mailCredentials())
        .build();
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
