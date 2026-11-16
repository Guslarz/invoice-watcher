package com.guslarz.invoicewatcher.userregistration.impl;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.ConfigurationConflictException;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthService;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationService {

  private final RegistrationDao dao;
  private final GoogleOauthService google;

  public String email(OidcUser user) {
    var issuer = user == null ? null : user.getIssuer();
    if (user == null
        || !Boolean.TRUE.equals(user.getEmailVerified())
        || issuer == null
        || !"https://accounts.google.com".equals(issuer.toString())) {
      throw new AccessDeniedException("Verified Google identity required");
    }
    return user.getEmail();
  }

  public String owner(OidcUser user) {
    email(user);
    return user.getIssuer() + "|" + user.getSubject();
  }

  public Optional<User> find(OidcUser user) {
    return dao.find(email(user), owner(user));
  }

  public User register(OidcUser identity, GmailOauthCredentials grant, List<FolderInput> folders) {
    if (grant == null || !grant.username().equals(email(identity))) {
      throw new IllegalArgumentException("Connect Gmail before registering");
    }
    return dao.create(
        User.builder()
            .name(email(identity))
            .ownerSubject(owner(identity))
            .configurationVersion(0L)
            .scanConfigs(
                List.of(
                    MailScanConfig.builder()
                        .server(new MailServer("imap.gmail.com", 993))
                        .credentials(grant)
                        .folders(folders(List.of(), folders))
                        .build()))
            .build());
  }

  public User edit(OidcUser identity, long version, List<FolderInput> inputs) {
    return dao.update(
        email(identity),
        owner(identity),
        version,
        user -> {
          if (user.scanConfigs().size() != 1) {
            throw new IllegalArgumentException("Connect Gmail before editing folders");
          }
          var config = user.scanConfigs().getFirst();
          return user.toBuilder()
              .scanConfigs(
                  List.of(
                      config.toBuilder()
                          .folders(folders(List.copyOf(config.folders()), inputs))
                          .build()))
              .build();
        });
  }

  public User connect(
      OidcUser identity, long version, GmailOauthCredentials grant, boolean freshRefreshToken) {
    return dao.update(
        email(identity),
        owner(identity),
        version,
        user -> {
          if (user.scanConfigs().isEmpty()) {
            throw new IllegalArgumentException("Save folders before connecting the mailbox");
          }
          var config = user.scanConfigs().getFirst();
          var previous = (GmailOauthCredentials) config.credentials();
          if (!freshRefreshToken && previous.reconnectRequired()) {
            throw new IllegalArgumentException("Offline access must be renewed");
          }
          return user.toBuilder()
              .scanConfigs(
                  List.of(
                      config.toBuilder()
                          .credentials(
                              grant.toBuilder()
                                  .refreshToken(
                                      freshRefreshToken
                                          ? grant.refreshToken()
                                          : previous.refreshToken())
                                  .grantId(previous.grantId())
                                  .revision(previous.revision() + 1)
                                  .build())
                          .build()))
              .build();
        });
  }

  public User connectConfigured(
      OidcUser identity, long version, GmailOauthCredentials grant, List<FolderInput> inputs) {
    var configured = folders(List.of(), inputs);
    return dao.update(
        email(identity),
        owner(identity),
        version,
        user -> {
          if (!user.scanConfigs().isEmpty() || !grant.username().equals(email(identity))) {
            throw new IllegalArgumentException("Mailbox connection changed");
          }
          return user.toBuilder()
              .scanConfigs(
                  List.of(
                      new MailScanConfig(new MailServer("imap.gmail.com", 993), grant, configured)))
              .build();
        });
  }

  public boolean disconnect(OidcUser identity, long version, boolean delete) {
    User current =
        find(identity).orElseThrow(() -> new IllegalArgumentException("Registration missing"));
    if (!Objects.equals(current.configurationVersion(), version)) {
      throw new ConfigurationConflictException();
    }
    // Revoke before dropping the only local copy; provider failure never blocks local deletion.
    boolean revoked = true;
    for (var config : current.scanConfigs()) {
      revoked &= google.revoke(((GmailOauthCredentials) config.credentials()).refreshToken());
    }
    if (delete) {
      dao.delete(email(identity), owner(identity), version);
    } else {
      dao.update(
          email(identity),
          owner(identity),
          version,
          u -> u.toBuilder().scanConfigs(List.of()).build());
    }
    return revoked;
  }

  private List<FolderConfig> folders(List<FolderConfig> current, List<FolderInput> inputs) {
    if (inputs == null || inputs.isEmpty() || inputs.size() > 16) {
      throw new IllegalArgumentException("Choose between 1 and 16 folders");
    }
    var names = new HashSet<String>();
    var ids = new HashSet<String>();
    return inputs.stream()
        .map(
            input -> {
              if (input.name() == null
                  || input.name().isBlank()
                  || input.name().length() > 255
                  || !names.add(input.name())
                  || (input.id() != null && !ids.add(input.id()))) {
                throw new IllegalArgumentException(
                    "Folder names must be unique and between 1 and 255 characters");
              }
              ZonedDateTime date =
                  input.startDate() == null || input.startDate().isBlank()
                      ? null
                      : ZonedDateTime.parse(input.startDate());
              if (input.id() == null) {
                return FolderConfig.builder().name(input.name()).startDate(date).build();
              }
              var old =
                  current.stream()
                      .filter(f -> input.id().equals(f.id()))
                      .findFirst()
                      .orElseThrow(() -> new IllegalArgumentException("Unknown folder"));
              if (!Objects.equals(old.startDate(), date)) {
                throw new IllegalArgumentException("An existing folder's start date is read-only");
              }
              return old.name().equals(input.name())
                  ? old
                  : FolderConfig.builder().name(input.name()).startDate(date).build();
            })
        .toList();
  }

  public record FolderInput(String id, String name, String startDate) {}
}
