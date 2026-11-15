package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.ConfigurationConflictException;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.GmailGrantDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.MailScanConfigEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials.GmailOauthCredentialsEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper.MailScanConfigMapper;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.repository.UserRepository;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class RegistrationDaoJpaImpl implements RegistrationDao, GmailGrantDao {

  private final UserRepository repository;
  private final UserMapper mapper;
  private final MailScanConfigMapper configMapper;

  @Override
  public Optional<User> find(String email, String subject) {
    repository
        .findByOwnerSubject(subject)
        .ifPresent(
            u -> {
              if (!u.name().equals(email)) {
                throw new ConfigurationConflictException();
              }
            });
    var entity = repository.findByName(email);
    if (entity.isPresent() && !Objects.equals(entity.get().ownerSubject(), subject)) {
      throw new ConfigurationConflictException();
    }
    return entity.map(mapper::convert);
  }

  @Override
  public User create(User user) {
    if (repository.findByName(user.name()).isPresent()
        || repository.findByOwnerSubject(user.ownerSubject()).isPresent()) {
      throw new ConfigurationConflictException();
    }
    return mapper.convert(repository.saveAndFlush(mapper.convert(user)));
  }

  @Override
  public User update(String email, String subject, long version, UnaryOperator<User> change) {
    var entity = repository.findByName(email).orElseThrow(ConfigurationConflictException::new);
    if (!Objects.equals(entity.ownerSubject(), subject)
        || !Objects.equals(entity.configurationVersion(), version)) {
      throw new ConfigurationConflictException();
    }
    User updated = change.apply(mapper.convert(entity));
    var retained = entity.scanConfigs();
    var next = new ArrayList<MailScanConfigEntity>();
    for (var config : updated.scanConfigs()) {
      var mapped = configMapper.convert(config);
      if (retained.isEmpty()) {
        next.add(mapped);
      } else {
        var existing = retained.getFirst();
        var folders = new ArrayList<FolderConfigEntity>();
        for (var folder : mapped.folders()) {
          var original =
              existing.folders().stream().filter(f -> f.id().equals(folder.id())).findFirst();
          folders.add(original.orElse(folder));
        }
        existing.folders().clear();
        existing.folders().addAll(folders);
        existing.credentials(mapped.credentials());
        next.add(existing);
      }
    }
    retained.clear();
    retained.addAll(next);
    entity.configurationVersion(version + 1);
    return mapper.convert(repository.saveAndFlush(entity));
  }

  @Override
  public void delete(String email, String subject, long version) {
    var entity = repository.findByName(email).orElseThrow(ConfigurationConflictException::new);
    if (!Objects.equals(entity.ownerSubject(), subject)
        || !Objects.equals(entity.configurationVersion(), version)) {
      throw new ConfigurationConflictException();
    }
    repository.delete(entity);
    repository.flush();
  }

  @Override
  public Optional<GmailOauthCredentials> findGrant(String email, String grantId) {
    return repository.findByName(email).map(mapper::convert).stream()
        .flatMap(u -> u.scanConfigs().stream())
        .map(c -> c.credentials())
        .filter(GmailOauthCredentials.class::isInstance)
        .map(GmailOauthCredentials.class::cast)
        .filter(c -> c.grantId().equals(grantId))
        .findFirst();
  }

  @Override
  public boolean updateGrant(
      String email, String grantId, long revision, GmailOauthCredentials next) {
    var user = repository.findByName(email);
    if (user.isEmpty()) {
      return false;
    }
    for (var config : user.get().scanConfigs()) {
      if (config.credentials() instanceof GmailOauthCredentialsEntity grant
          && grant.grantId().equals(grantId)
          && grant.grantRevision() == revision) {
        grant.refreshToken(next.refreshToken().getEncrypted());
        grant.grantRevision(revision + 1);
        grant.reconnectRequired(next.reconnectRequired());
        repository.flush();
        return true;
      }
    }
    return false;
  }
}
