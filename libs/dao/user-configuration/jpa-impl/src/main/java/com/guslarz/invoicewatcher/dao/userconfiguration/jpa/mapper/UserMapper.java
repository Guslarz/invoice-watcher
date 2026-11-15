package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

  private final MailScanConfigMapper mailScanConfigMapper;

  public User convert(UserEntity user) {
    return User.builder()
        .id(user.id() != null ? String.valueOf(user.id()) : null)
        .name(user.name())
        .ownerSubject(user.ownerSubject())
        .configurationVersion(user.configurationVersion())
        .scanConfigs(user.scanConfigs().stream().map(mailScanConfigMapper::convert).toList())
        .build();
  }

  public UserEntity convert(User user) {
    return UserEntity.builder()
        .id(user.id() != null ? Long.parseLong(user.id()) : null)
        .name(user.name())
        .ownerSubject(user.ownerSubject())
        .configurationVersion(user.configurationVersion())
        .scanConfigs(user.scanConfigs().stream().map(mailScanConfigMapper::convert).toList())
        .build();
  }
}
