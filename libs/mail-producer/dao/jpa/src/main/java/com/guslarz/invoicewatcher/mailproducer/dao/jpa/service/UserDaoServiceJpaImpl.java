package com.guslarz.invoicewatcher.mailproducer.dao.jpa.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.UserEntity;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.FolderConfigRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.MailCredentialsRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.MailScanConfigRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserDaoServiceJpaImpl implements UserDaoService {

  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final MailCredentialsRepository mailCredentialsRepository;
  private final MailScanConfigRepository mailScanConfigRepository;
  private final FolderConfigRepository folderConfigRepository;

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
    userRepository.deleteByName(user.name());
    userRepository.flush();
    newUserEntity.scanConfigs().forEach(scanConfig -> {
      mailCredentialsRepository.save(scanConfig.credentials());
      folderConfigRepository.saveAll(scanConfig.folders());
    });
    mailScanConfigRepository.saveAll(newUserEntity.scanConfigs());
    userRepository.save(newUserEntity);
  }
}
