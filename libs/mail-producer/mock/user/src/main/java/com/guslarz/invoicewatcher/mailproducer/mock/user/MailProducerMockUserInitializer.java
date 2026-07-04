package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        .mailServer(getMailServer())
        .mailCredentials(getMailCredentials())
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
