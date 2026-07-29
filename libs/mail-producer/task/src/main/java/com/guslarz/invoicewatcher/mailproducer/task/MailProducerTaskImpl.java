package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionProperties;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailProducerTaskImpl implements MailProducerTask {

  private final UserDaoService userDaoService;
  private final FolderConfigDaoService folderConfigDaoService;
  private final MailConnectionFactory mailConnectionFactory;
  private final MailProcessorService mailProcessorService;

  @Override
  public void execute() {
    userDaoService.findAll().forEach(this::processUser);
  }

  private void processUser(User user) {
    log.info("> Processing user: {}", user.name());
    user.scanConfigs().forEach(scanConfig -> scan(user, scanConfig));
    log.info("< Finished processing user: {}", user.name());
  }

  private void scan(User user, MailScanConfig scanConfig) {
    log.info("> Scanning for config: {}", scanConfig.server().host());
    try (MailConnection mailConnection = mailConnectionFactory.createConnection(connectionProperties(scanConfig))) {
      scanConfig.folders().forEach(folderConfig -> scanFolder(mailConnection, MailProcessorContext.builder()
          .user(user)
          .scanConfig(scanConfig)
          .folderConfig(folderConfig)
          .build()));
    } catch (Exception e) {
      log.error("Error processing scan config: {}", scanConfig, e);
    }
    log.info("< Finished scanning for config: {}", scanConfig.server().host());
  }

  private void scanFolder(MailConnection mailConnection, MailProcessorContext context) {
    try {
      log.info("> Scanning folder: {}", context.folderConfig().name());
      processEmailMessages(context, mailConnection.getEmailMessages(query(context.folderConfig())));
      log.info("< Finished scanning folder: {}", context.folderConfig().name());
    } catch (Exception e) {
      log.error("Error scanning folder: {}", context.folderConfig(), e);
      throw e;
    }
  }

  private void processEmailMessages(MailProcessorContext context, List<MailMessage> mailMessages) {
    if (mailMessages.isEmpty()) {
      return;
    }
    for (MailMessage message : mailMessages) {
      mailProcessorService.processMail(context, message);
    }
    folderConfigDaoService.updateScanTimestamp(context.folderConfig(), mailMessages.getFirst().date());
  }

  private MailConnectionProperties connectionProperties(MailScanConfig scanConfig) {
    return MailConnectionProperties.builder()
            .host(scanConfig.server().host())
            .port(scanConfig.server().port())
            .mailCredentials(scanConfig.credentials())
            .build();
  }

  private MailQuery query(FolderConfig folderConfig) {
    return MailQuery.builder()
            .folder(folderConfig.name())
            .dateFrom(folderConfig.scanTimestamp())
            .build();
  }
}
