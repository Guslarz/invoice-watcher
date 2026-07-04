package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.UserEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.MailCredentialsRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserDaoServiceJpaImpl implements UserDaoService {

  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final MailCredentialsRepository mailCredentialsRepository;

  @Override
  public List<User> findAll() {
    return userRepository.findAll().stream()
        .map(userMapper::convert)
        .toList();
  }

  @Override
  public void add(User user) {
    userRepository.save(userMapper.convert(user));
  }

  @Override
  @Transactional
  public void upsertUserByName(User user) {
    UserEntity newUserEntity = userMapper.convert(user);
    Optional<Long> id = userRepository.findByName(user.name())
        .map(UserEntity::id);
    id.ifPresent(userRepository::deleteById);
    newUserEntity.id(id.orElse(null));
    mailCredentialsRepository.save(newUserEntity.mailCredentials());
    userRepository.save(newUserEntity);
  }
}
