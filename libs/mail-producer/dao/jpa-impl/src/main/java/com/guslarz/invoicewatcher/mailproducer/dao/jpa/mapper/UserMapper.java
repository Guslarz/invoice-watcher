package com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.UserEntity;
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
        .scanConfigs(user.scanConfigs().stream()
            .map(mailScanConfigMapper::convert)
            .toList())
        .build();
  }

  public UserEntity convert(User user) {
    return UserEntity.builder()
        .id(user.id() != null ? Long.parseLong(user.id()) : null)
        .name(user.name())
        .scanConfigs(user.scanConfigs().stream()
            .map(mailScanConfigMapper::convert)
            .toList())
        .build();
  }
}
