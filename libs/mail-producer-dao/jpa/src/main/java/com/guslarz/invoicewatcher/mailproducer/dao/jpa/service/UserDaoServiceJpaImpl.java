package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserDaoServiceJpaImpl implements UserDaoService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;

  @Override
  public void add(User user) {
    userRepository.save(userMapper.convert(user));
  }

  @Override
  public List<User> findAll() {
    return userRepository.findAll().stream()
        .map(userMapper::convert)
        .toList();
  }
}
