package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class MailProducerMockUserInitializer {

  private final MailProducerMockUserProperties properties;
  private final UserDaoService userDaoService;

  @PostConstruct
  void upsertMockUser() {
    userDaoService.upsertUserByName(buildUser());
  }

  private User buildUser() {
    return User.builder()
        .name(properties.email())
        .scanConfigs(Collections.singletonList(buildMailScanConfig()))
        .build();
  }

  private MailScanConfig buildMailScanConfig() {
    ZonedDateTime startDate = ZonedDateTime.now().minusDays(10);
    return MailScanConfig.builder()
        .server(getMailServer())
        .credentials(getMailCredentials())
        .folders(properties.folders().stream()
            .map(folderName -> FolderConfig.builder()
                .name(folderName)
                .startDate(startDate)
                .build())
            .toList())
        .build();
  }

  private MailServer getMailServer() {
    return MailServer.builder()
        .host(properties.host())
        .port(properties.port())
        .build();
  }

  private MailCredentials getMailCredentials() {
    return BasicAuthMailCredentials.builder()
        .username(properties.email())
        .password(properties.password())
        .build();
  }
}
