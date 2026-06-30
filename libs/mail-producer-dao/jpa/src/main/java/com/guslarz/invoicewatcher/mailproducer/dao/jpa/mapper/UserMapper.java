package com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.MailServerEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public User convert(UserEntity user) {
    return User.builder()
        .id(user.id())
        .mailServer(convert(user.mailServer()))
        .build();
  }

  public UserEntity convert(User user) {
    return UserEntity.builder()
        .id(user.id())
        .mailServer(convert(user.mailServer()))
        .build();
  }

  private MailServer convert(MailServerEntity mailServer) {
    return MailServer.builder()
        .host(mailServer.host())
        .port(mailServer.port())
        .build();
  }

  private MailServerEntity convert(MailServer mailServer) {
    return MailServerEntity.builder()
        .host(mailServer.host())
        .port(mailServer.port())
        .build();
  }
}
