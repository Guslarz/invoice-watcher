package com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BaseMailCredentialsVisitor;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.MailCredentialsDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.MailScanConfigDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.MailServerDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FirestoreMapper {

  private final SecretFactory secretFactory;

  public User toUser(UserDocument doc, List<FolderConfigDocument> folderDocs) {
    return User.builder()
        .id(doc.getId())
        .name(doc.getName())
        .scanConfigs(doc.getScanConfigs().stream()
            .map(scanConfig -> toMailScanConfig(scanConfig, folderDocs))
            .toList())
        .build();
  }

  public UserDocument toUserDocument(User user) {
    return UserDocument.builder()
        .id(user.name())
        .name(user.name())
        .scanConfigs(user.scanConfigs().stream()
            .map(this::toMailScanConfigDocument)
            .toList())
        .build();
  }

  public FolderConfigDocument toFolderConfigDocument(FolderConfig folder) {
    return FolderConfigDocument.builder()
        .id(folder.id())
        .name(folder.name())
        .scanTimestamp(folder.scanTimestamp() != null ? folder.scanTimestamp().toString() : null)
        .build();
  }

  public FolderConfig toFolderConfig(FolderConfigDocument doc) {
    return FolderConfig.builder()
        .id(doc.getId())
        .name(doc.getName())
        .scanTimestamp(doc.getScanTimestamp() != null ? ZonedDateTime.parse(doc.getScanTimestamp()) : null)
        .build();
  }

  private MailScanConfig toMailScanConfig(MailScanConfigDocument doc, List<FolderConfigDocument> folderDocs) {
    List<FolderConfig> folders = folderDocs.stream()
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
    List<String> folderIds = scanConfig.folders().stream()
        .map(FolderConfig::id)
        .toList();

    return MailScanConfigDocument.builder()
        .server(toMailServerDocument(scanConfig.server()))
        .credentials(toMailCredentialsDocument(scanConfig.credentials()))
        .folderIds(folderIds)
        .build();
  }

  private MailServer toMailServer(MailServerDocument doc) {
    return MailServer.builder()
        .host(doc.getHost())
        .port(doc.getPort())
        .build();
  }

  private MailServerDocument toMailServerDocument(MailServer server) {
    return MailServerDocument.builder()
        .host(server.host())
        .port(server.port())
        .build();
  }

  private MailCredentials toMailCredentials(MailCredentialsDocument doc) {
    if ("basic".equals(doc.getType())) {
      return BasicAuthMailCredentials.builder()
          .username(doc.getUsername())
          .password(secretFactory.fromEncrypted(doc.getPassword()))
          .build();
    }
    throw new IllegalArgumentException("Unknown credentials type: " + doc.getType());
  }

  private MailCredentialsDocument toMailCredentialsDocument(MailCredentials credentials) {
    return credentials.accept(new BaseMailCredentialsVisitor<>() {
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
