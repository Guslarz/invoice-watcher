package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.service;

import com.google.cloud.datastore.DatastoreException;
import com.google.cloud.spring.data.datastore.core.DatastoreOperations;
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.ConfigurationConflictException;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.GmailGrantDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.OwnerDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.mapper.FirestoreMapper;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationDaoFirestoreImpl implements RegistrationDao, GmailGrantDao {

  @Qualifier("daoFirestoreDatastoreTemplate")
  private final DatastoreTemplate template;

  private final FirestoreMapper mapper;

  @Override
  public Optional<User> find(String email, String subject) {
    return transact(
        tx -> {
          var binding = tx.findById(subject, OwnerDocument.class);
          if (binding != null && !binding.getEmail().equals(email)) {
            throw new ConfigurationConflictException();
          }
          var doc = tx.findById(email, UserDocument.class);
          if (doc != null && !Objects.equals(doc.getOwnerSubject(), subject)) {
            throw new ConfigurationConflictException();
          }
          return doc == null || !Objects.equals(doc.getOwnerSubject(), subject)
              ? Optional.empty()
              : Optional.of(read(tx, doc));
        });
  }

  @Override
  public User create(User user) {
    return transact(
        tx -> {
          if (tx.findById(user.name(), UserDocument.class) != null) {
            throw new ConfigurationConflictException();
          }
          var binding = tx.findById(user.ownerSubject(), OwnerDocument.class);
          if (binding != null) {
            throw new ConfigurationConflictException();
          }
          tx.save(new OwnerDocument(user.ownerSubject(), user.name()));
          User identified = assignFolderIds(user, this::assignNewFolderId);
          save(tx, identified);
          return identified;
        });
  }

  @Override
  public User update(String email, String subject, long version, UnaryOperator<User> change) {
    return transact(
        tx -> {
          var doc = owned(tx, email, subject, version);
          User current = read(tx, doc);
          User next = change.apply(current).toBuilder().configurationVersion(version + 1).build();
          next = assignFolderIds(next, this::assignMissingFolderId);
          List<String> nextIds =
              next.scanConfigs().stream()
                  .flatMap(c -> c.folders().stream())
                  .map(FolderConfig::id)
                  .toList();
          List<String> removed = ids(doc).stream().filter(id -> !nextIds.contains(id)).toList();
          tx.deleteAllById(removed, FolderConfigDocument.class);
          save(tx, next);
          return next;
        });
  }

  @Override
  public void delete(String email, String subject, long version) {
    transact(
        tx -> {
          var doc = owned(tx, email, subject, version);
          tx.deleteAllById(ids(doc), FolderConfigDocument.class);
          tx.deleteById(email, UserDocument.class);
          tx.deleteById(subject, OwnerDocument.class);
          return null;
        });
  }

  private User assignFolderIds(User user, UnaryOperator<FolderConfig> assignId) {
    var scanConfigs =
        user.scanConfigs().stream().map(config -> assignFolderIds(config, assignId)).toList();
    return user.toBuilder().scanConfigs(scanConfigs).build();
  }

  private MailScanConfig assignFolderIds(
      MailScanConfig config, UnaryOperator<FolderConfig> assignId) {
    var folders = config.folders().stream().map(assignId).toList();
    return config.toBuilder().folders(folders).build();
  }

  private FolderConfig assignNewFolderId(FolderConfig folder) {
    return folder.toBuilder().id(UUID.randomUUID().toString()).build();
  }

  private FolderConfig assignMissingFolderId(FolderConfig folder) {
    return folder.id() == null ? assignNewFolderId(folder) : folder;
  }

  private UserDocument owned(DatastoreOperations tx, String email, String subject, long version) {
    var doc = tx.findById(email, UserDocument.class);
    if (doc == null
        || !Objects.equals(doc.getOwnerSubject(), subject)
        || !Objects.equals(doc.getConfigurationVersion(), version)) {
      throw new ConfigurationConflictException();
    }
    return doc;
  }

  private User read(DatastoreOperations tx, UserDocument doc) {
    List<FolderConfigDocument> folders = new ArrayList<>();
    tx.findAllById(ids(doc), FolderConfigDocument.class).forEach(folders::add);
    return mapper.toUser(doc, folders);
  }

  private List<String> ids(UserDocument doc) {
    return doc.getScanConfigs().stream().flatMap(c -> c.getFolderIds().stream()).toList();
  }

  private void save(DatastoreOperations tx, User user) {
    for (var config : user.scanConfigs()) {
      for (var folder : config.folders()) {
        tx.save(mapper.toFolderConfigDocument(folder));
      }
    }
    tx.save(mapper.toUserDocument(user));
  }

  @Override
  public Optional<GmailOauthCredentials> findGrant(String email, String grantId) {
    return transact(
        tx -> {
          var doc = tx.findById(email, UserDocument.class);
          return doc == null
              ? Optional.empty()
              : read(tx, doc).scanConfigs().stream()
                  .map(c -> c.credentials())
                  .filter(GmailOauthCredentials.class::isInstance)
                  .map(GmailOauthCredentials.class::cast)
                  .filter(c -> c.grantId().equals(grantId))
                  .findFirst();
        });
  }

  @Override
  public boolean updateGrant(
      String email, String grantId, long revision, GmailOauthCredentials next) {
    return transact(
        tx -> {
          var doc = tx.findById(email, UserDocument.class);
          if (doc == null) {
            return false;
          }
          for (var config : doc.getScanConfigs()) {
            var grant = config.getCredentials();
            if (grantId.equals(grant.getGrantId())
                && Objects.equals(grant.getGrantRevision(), revision)) {
              grant.setRefreshToken(next.refreshToken().getEncrypted());
              grant.setGrantRevision(revision + 1);
              grant.setReconnectRequired(next.reconnectRequired());
              tx.save(doc);
              return true;
            }
          }
          return false;
        });
  }

  private <T> T transact(Function<DatastoreOperations, T> work) {
    try {
      return template.performTransaction(work);
    } catch (DatastoreException failure) {
      Throwable cause = failure.getCause();
      while (cause != null) {
        if (cause instanceof ConfigurationConflictException conflict) {
          throw conflict;
        }
        if (cause instanceof IllegalArgumentException invalid) {
          throw invalid;
        }
        if (cause instanceof DateTimeException invalidDate) {
          throw invalidDate;
        }
        cause = cause.getCause();
      }
      throw failure;
    }
  }
}
