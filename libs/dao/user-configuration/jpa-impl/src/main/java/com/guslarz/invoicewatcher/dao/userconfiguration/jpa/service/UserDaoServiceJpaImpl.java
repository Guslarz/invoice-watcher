package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.UserEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserDaoServiceJpaImpl implements UserDaoService {

  private final UserMapper userMapper;
  private final UserRepository userRepository;

  @Override
  public List<User> findAll() {
    return userRepository.findAll().stream().map(userMapper::convert).toList();
  }

  @Override
  @Transactional
  public void upsertUserByName(User user) {
    UserEntity newUserEntity = userMapper.convert(user);
    userRepository.deleteByName(user.name());
    userRepository.flush();
    userRepository.save(newUserEntity);
  }
}
