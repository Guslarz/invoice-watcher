package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.mapper;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailCredentialsDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailCredentialsDocumentVisitor;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailScanConfigDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.MailServerDocument;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BaseMailCredentialsVisitor;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FirestoreMapper {

  private final SecretFactory secretFactory;

  public User toUser(UserDocument doc, List<FolderConfigDocument> folderDocs) {
    return User.builder()
        .id(doc.getId())
        .name(doc.getName())
        .ownerSubject(doc.getOwnerSubject())
        .configurationVersion(doc.getConfigurationVersion())
        .scanConfigs(
            Optional.ofNullable(doc.getScanConfigs()).orElse(List.of()).stream()
                .map(scanConfig -> toMailScanConfig(scanConfig, folderDocs))
                .toList())
        .build();
  }

  public UserDocument toUserDocument(User user) {
    return UserDocument.builder()
        .id(user.name())
        .name(user.name())
        .ownerSubject(user.ownerSubject())
        .configurationVersion(user.configurationVersion())
        .scanConfigs(user.scanConfigs().stream().map(this::toMailScanConfigDocument).toList())
        .build();
  }

  public FolderConfigDocument toFolderConfigDocument(FolderConfig folder) {
    return FolderConfigDocument.builder()
        .id(folder.id())
        .name(folder.name())
        .startDate(folder.startDate() != null ? folder.startDate().toString() : null)
        .uidValidity(folder.uidValidity())
        .lastUid(folder.lastUid())
        .build();
  }

  public FolderConfig toFolderConfig(FolderConfigDocument doc) {
    return FolderConfig.builder()
        .id(doc.getId())
        .name(doc.getName())
        .startDate(doc.getStartDate() != null ? ZonedDateTime.parse(doc.getStartDate()) : null)
        .uidValidity(doc.getUidValidity())
        .lastUid(doc.getLastUid())
        .build();
  }

  private MailScanConfig toMailScanConfig(
      MailScanConfigDocument doc, List<FolderConfigDocument> folderDocs) {
    List<FolderConfig> folders =
        folderDocs.stream()
            .filter(f -> doc.getFolderIds().contains(f.getId()))
            .map(this::toFolderConfig)
            .toList();

    return MailScanConfig.builder()
        .server(toMailServer(doc.getServer()))
        .credentials(toMailCredentials(doc.getCredentials()))
        .folders(folders)
        .build();
  }

  private MailScanConfigDocument toMailScanConfigDocument(MailScanConfig scanConfig) {
    List<String> folderIds = scanConfig.folders().stream().map(FolderConfig::id).toList();

    return MailScanConfigDocument.builder()
        .server(toMailServerDocument(scanConfig.server()))
        .credentials(toMailCredentialsDocument(scanConfig.credentials()))
        .folderIds(folderIds)
        .build();
  }

  private MailServer toMailServer(MailServerDocument doc) {
    return MailServer.builder().host(doc.getHost()).port(doc.getPort()).build();
  }

  private MailServerDocument toMailServerDocument(MailServer server) {
    return MailServerDocument.builder().host(server.host()).port(server.port()).build();
  }

  private MailCredentials toMailCredentials(MailCredentialsDocument doc) {
    return doc.accept(
        new MailCredentialsDocumentVisitor<>() {
          @Override
          public MailCredentials visitBasicAuthMailCredentialsDocument(MailCredentialsDocument value) {
            return BasicAuthMailCredentials.builder()
                .username(value.getUsername())
                .password(secretFactory.fromEncrypted(value.getPassword()))
                .build();
          }

          @Override
          public MailCredentials visitGmailOauthCredentialsDocument(MailCredentialsDocument value) {
            return new GmailOauthCredentials(
                value.getUsername(),
                value.getGrantId(),
                secretFactory.fromEncrypted(value.getRefreshToken()),
                value.getGrantRevision() == null ? 0 : value.getGrantRevision(),
                Boolean.TRUE.equals(value.getReconnectRequired()));
          }
        });
  }

  private MailCredentialsDocument toMailCredentialsDocument(MailCredentials credentials) {
    return credentials.accept(
        new BaseMailCredentialsVisitor<>() {
          @Override
          public MailCredentialsDocument visitGmailOauthCredentials(GmailOauthCredentials creds) {
            return MailCredentialsDocument.builder()
                .type("gmail-oauth")
                .username(creds.username())
                .grantId(creds.grantId())
                .refreshToken(creds.refreshToken().getEncrypted())
                .grantRevision(creds.revision())
                .reconnectRequired(creds.reconnectRequired())
                .build();
          }

          @Override
          public MailCredentialsDocument visitBasicMailCredentials(BasicAuthMailCredentials creds) {
            return MailCredentialsDocument.builder()
                .type("basic")
                .username(creds.username())
                .password(creds.password().getEncrypted())
                .build();
          }
        });
  }
}
