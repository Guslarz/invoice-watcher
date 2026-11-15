package com.guslarz.invoicewatcher.dao.userconfiguration.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.datastore.Datastore;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.ConfigurationConflictException;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.GmailGrantDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailconnector.api.ScanPosition;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.gcloud.DatastoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RegistrationDatastoreIntegrationTest {
  @Container
  private static DatastoreEmulatorContainer emulator =
      new DatastoreEmulatorContainer(
              DockerImageName.parse(
                  "gcr.io/google.com/cloudsdktool/google-cloud-cli:578.0.0-emulators"))
          .withFlags("--consistency=1 --no-store-on-disk --quiet");

  @Test
  @DisplayName(
      "REG-002 REG-006 REG-007 REG-015 USER-004: Datastore registration and scanning share atomic"
          + " state")
  void preservesPositionsAndRejectsStaleWrites() {
    // given
    var factory = mock(SecretFactory.class);
    when(factory.fromEncrypted(anyString())).thenAnswer(call -> secret(call.getArgument(0)));
    var uut =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(UserConfigurationDaoFirestoreAutoConfiguration.class))
            .withBean(SecretFactory.class, () -> factory)
            // The DAO uses SDK project discovery, not Spring's GCP project property.
            .withSystemProperties("GOOGLE_CLOUD_PROJECT=" + emulator.getProjectId())
            .withPropertyValues(
                "invoice-watcher.user-configuration.dao.mode=firestore",
                "invoice-watcher.user-configuration.dao.firestore.host=http://"
                    + emulator.getEmulatorEndpoint());
    uut.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(Datastore.class).getOptions().getProjectId())
              .isEqualTo(emulator.getProjectId());
          var registration = context.getBean(RegistrationDao.class);
          var scanner = context.getBean(UserDaoService.class);
          var positions = context.getBean(FolderConfigDaoService.class);
          final var grants = context.getBean(GmailGrantDao.class);
          String email = UUID.randomUUID() + "@example.com";
          String owner = "google|" + UUID.randomUUID();
          var credentials =
              new GmailOauthCredentials(email, "grant", secret("encrypted-refresh"), 0, false);
          var source =
              User.builder()
                  .name(email)
                  .ownerSubject(owner)
                  .configurationVersion(0L)
                  .scanConfigs(
                      List.of(
                          new MailScanConfig(
                              new MailServer("imap.gmail.com", 993),
                              credentials,
                              List.of(FolderConfig.builder().name("INBOX").build()))))
                  .build();
          // when
          var created = registration.create(source);
          var folder = created.scanConfigs().getFirst().folders().iterator().next();
          positions.updateScanPosition(folder, new ScanPosition(7, 42));
          var updated = registration.update(email, owner, 0, user -> user);
          // then
          assertThat(updated.scanConfigs().getFirst().folders())
              .singleElement()
              .satisfies(f -> assertThat(f.lastUid()).isEqualTo(42));
          assertThat(scanner.findAll()).anySatisfy(u -> assertThat(u.name()).isEqualTo(email));
          assertThat(catchThrowable(() -> registration.update(email, owner, 0, user -> user)))
              .isInstanceOf(ConfigurationConflictException.class);
          assertThat(
                  grants.updateGrant(
                      email, "grant", 0, credentials.toBuilder().reconnectRequired(true).build()))
              .isTrue();
          assertThat(grants.updateGrant(email, "grant", 0, credentials)).isFalse();
          assertThat(catchThrowable(() -> registration.find("changed-" + email, owner)))
              .isInstanceOf(ConfigurationConflictException.class);
          registration.delete(email, owner, 1);
          positions.updateScanPosition(folder, new ScanPosition(7, 43));
          assertThat(registration.find(email, owner)).isEmpty();
          assertThat(grants.updateGrant(email, "grant", 1, credentials)).isFalse();
        });
  }

  private static Secret secret(String encrypted) {
    var value = mock(Secret.class);
    when(value.getEncrypted()).thenReturn(encrypted);
    return value;
  }
}
