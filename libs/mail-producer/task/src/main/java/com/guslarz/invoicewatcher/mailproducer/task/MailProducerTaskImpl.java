package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnection;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionProperties;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import com.guslarz.invoicewatcher.mailconnector.api.MailQuery;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
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
  private final MailConnectionFactory mailConnectionFactory;

  @Override
  public void execute() {
    userDaoService.findAll().forEach(this::processUser);
  }

  private void processUser(User user) {
    log.info("> Processing user: {}", user.name());
    user.scanConfigs().forEach(this::processScanConfig);
    log.info("< Finished processing user: {}", user.name());
  }

  private void processScanConfig(MailScanConfig scanConfig) {
    log.info("> Processing scan config: {}", scanConfig.server().host());
    try (MailConnection mailConnection = mailConnectionFactory.createConnection(connectionProperties(scanConfig))) {
      scanConfig.folders().forEach(folderConfig -> processFolder(mailConnection, folderConfig));
    } catch (Exception e) {
      log.error("Error processing scan config: {}", scanConfig, e);
    }
    log.info("< Finished processing scan config: {}", scanConfig.server().host());
  }

  private void processFolder(MailConnection mailConnection, FolderConfig folderConfig) {
    try {
      log.info("> Processing folder: {}", folderConfig.name());
      processEmailMessages(mailConnection.getEmailMessages(query(folderConfig)));
      log.info("< Finished processing folder: {}", folderConfig.name());
    } catch (Exception e) {
      log.error("Error processing folder: {}", folderConfig, e);
    }
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

  private void processEmailMessages(List<MailMessage> emailMessages) {
    for (MailMessage message : emailMessages) {
      log.info("Processing email message: {}", message);
    }
  }
}
