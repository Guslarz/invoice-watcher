package com.guslarz.invoicewatcher.dao.userconfiguration.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

class UserConfigurationDaoJpaAutoConfigurationTest {

  private static final String DAO = "invoice-watcher.user-configuration.dao.";
  private static final String JPA = DAO + "jpa.";

  private final ApplicationContextRunner uut =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  HibernateJpaAutoConfiguration.class,
                  TransactionAutoConfiguration.class,
                  UserConfigurationDaoJpaAutoConfiguration.class))
          .withBean(
              SecretFactory.class, UserConfigurationDaoJpaAutoConfigurationTest::secretFactory)
          .withPropertyValues(
              DAO + "mode=jpa",
              JPA + "ddl-auto=create",
              JPA + "dialect=org.hibernate.dialect.H2Dialect",
              JPA + "data-source.basic.url=jdbc:h2:mem:" + UUID.randomUUID(),
              JPA + "data-source.basic.driver=org.h2.Driver");

  private static SecretFactory secretFactory() {
    SecretFactory factory = mock(SecretFactory.class);
    when(factory.fromEncrypted(anyString()))
        .thenAnswer(invocation -> secret(invocation.getArgument(0)));
    return factory;
  }

  private static Secret secret(String encrypted) {
    Secret secret = mock(Secret.class);
    when(secret.getEncrypted()).thenReturn(encrypted);
    return secret;
  }

  private static User mockUser(String host, String password, List<String> folders) {
    ZonedDateTime startDate = ZonedDateTime.now().minusDays(10);
    return User.builder()
        .name("jan@example.com")
        .scanConfigs(
            List.of(
                MailScanConfig.builder()
                    .server(MailServer.builder().host(host).port(993).build())
                    .credentials(
                        BasicAuthMailCredentials.builder()
                            .username("jan@example.com")
                            .password(secret(password))
                            .build())
                    .folders(
                        folders.stream()
                            .map(
                                name ->
                                    FolderConfig.builder().name(name).startDate(startDate).build())
                            .toList())
                    .build()))
        .build();
  }

  private static int rows(JdbcTemplate jdbc, String table) {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    return count == null ? 0 : count;
  }

  @Test
  @DisplayName("USER-031: replacing the mock user leaves only the new user's rows")
  void replacesTheWholeUserGraph() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(
        context -> {
          UserDaoService userDaoService = context.getBean(UserDaoService.class);
          userDaoService.upsertUserByName(
              mockUser("old.example.com", "old", List.of("INBOX", "Invoices")));
          userDaoService.upsertUserByName(mockUser("new.example.com", "new", List.of("Archive")));

          // then
          List<User> users = userDaoService.findAll();
          assertThat(users)
              .singleElement()
              .satisfies(
                  user -> {
                    assertThat(user.name()).isEqualTo("jan@example.com");
                    assertThat(user.scanConfigs())
                        .singleElement()
                        .satisfies(
                            scanConfig -> {
                              assertThat(scanConfig.server().host()).isEqualTo("new.example.com");
                              assertThat(
                                      ((BasicAuthMailCredentials) scanConfig.credentials())
                                          .password()
                                          .getEncrypted())
                                  .isEqualTo("new");
                              assertThat(scanConfig.folders())
                                  .extracting(FolderConfig::name)
                                  .containsExactly("Archive");
                            });
                  });
          JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
          assertThat(rows(jdbc, "user_entity")).isEqualTo(1);
          assertThat(rows(jdbc, "mail_scan_config_entity")).isEqualTo(1);
          assertThat(rows(jdbc, "mail_credentials_entity")).isEqualTo(1);
          assertThat(rows(jdbc, "folder_config_entity")).isEqualTo(1);
          assertThat(rows(jdbc, "user_entity_scan_configs")).isEqualTo(1);
          assertThat(rows(jdbc, "mail_scan_config_entity_folders")).isEqualTo(1);
        });
  }

  @Test
  @DisplayName(
      "stores all credentials in one table with a type discriminator and a required server")
  void generatesSingleCredentialsTableAndRequiredServer() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(
        context -> {
          context
              .getBean(UserDaoService.class)
              .upsertUserByName(mockUser("imap.example.com", "secret", List.of("INBOX")));

          // then
          JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
          assertThat(
                  jdbc.queryForList(
                      "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                          + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'"
                          + " AND TABLE_NAME LIKE '%CREDENTIALS%'",
                      String.class))
              .containsExactly("MAIL_CREDENTIALS_ENTITY");
          assertThat(jdbc.queryForList("SELECT type FROM mail_credentials_entity", String.class))
              .containsExactly("basic");
          assertThat(
                  jdbc.queryForList(
                      "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                          + " WHERE TABLE_NAME = 'MAIL_SCAN_CONFIG_ENTITY' AND IS_NULLABLE = 'NO'",
                      String.class))
              .contains("HOST", "PORT");
        });
  }

  @Test
  @DisplayName(
      "REG-002 REG-006 REG-007 REG-015 USER-004: shared JPA preserves progress and deletes"
          + " atomically")
  void sharesRegistrationWithScanner() {
    // given
    uut.run(
        context -> {
          var registration = context.getBean(RegistrationDao.class);
          var scanner = context.getBean(UserDaoService.class);
          var positions = context.getBean(FolderConfigDaoService.class);
          var source =
              mockUser("imap.gmail.com", "secret", List.of("INBOX")).toBuilder()
                  .ownerSubject("google|subject")
                  .configurationVersion(0L)
                  .build();
          // when
          var created = registration.create(source);
          var folder = created.scanConfigs().getFirst().folders().iterator().next();
          positions.updateScanPosition(folder, new ScanPosition(7, 42));
          var updated = registration.update(source.name(), source.ownerSubject(), 0, user -> user);
          // then
          assertThat(updated.configurationVersion()).isEqualTo(1);
          assertThat(scanner.findAll())
              .singleElement()
              .satisfies(
                  user ->
                      assertThat(user.scanConfigs().getFirst().folders())
                          .singleElement()
                          .satisfies(f -> assertThat(f.lastUid()).isEqualTo(42)));
          var conflict =
              Assertions.catchThrowable(
                  () -> registration.update(source.name(), source.ownerSubject(), 0, user -> user));
          assertThat(conflict).isInstanceOf(ConfigurationConflictException.class);
          registration.delete(source.name(), source.ownerSubject(), 1);
          positions.updateScanPosition(folder, new ScanPosition(7, 43));
          assertThat(scanner.findAll()).isEmpty();
        });
  }

  @Test
  @DisplayName(
      "USER-002 SECRET-007: JPA stores typed encrypted OAuth grants and conditional revisions")
  void roundTripsOauthGrant() {
    // given
    uut.run(
        context -> {
          var registration = context.getBean(RegistrationDao.class);
          var grants = context.getBean(GmailGrantDao.class);
          var credentials =
              new GmailOauthCredentials(
                  "jan@example.com", "grant", secret("encrypted-refresh"), 0, false);
          var source =
              mockUser("imap.gmail.com", "secret", List.of("INBOX")).toBuilder()
                  .ownerSubject("google|subject")
                  .configurationVersion(0L)
                  .scanConfigs(
                      List.of(
                          mockUser("imap.gmail.com", "secret", List.of("INBOX"))
                              .scanConfigs()
                              .getFirst()
                              .toBuilder()
                              .credentials(credentials)
                              .build()))
                  .build();
          // when
          registration.create(source);
          boolean changed =
              grants.updateGrant(
                  source.name(),
                  "grant",
                  0,
                  credentials.toBuilder().reconnectRequired(true).build());
          // then
          assertThat(changed).isTrue();
          assertThat(grants.findGrant(source.name(), "grant"))
              .hasValueSatisfying(
                  grant -> {
                    assertThat(grant.refreshToken().getEncrypted()).isEqualTo("encrypted-refresh");
                    assertThat(grant.revision()).isEqualTo(1);
                    assertThat(grant.reconnectRequired()).isTrue();
                  });
          assertThat(grants.updateGrant(source.name(), "grant", 0, credentials)).isFalse();
        });
  }

  @Test
  @DisplayName("USER-014 REG-002: legacy password users cannot be claimed by email matching")
  void rejectsLegacyEmailClaim() {
    // given
    uut.run(
        context -> {
          var provisioning = context.getBean(UserDaoService.class);
          var registration = context.getBean(RegistrationDao.class);
          var legacy = mockUser("imap.example.com", "legacy-secret", List.of("INBOX"));
          provisioning.upsertUserByName(legacy);
          // when
          var failure =
              Assertions.catchThrowable(() -> registration.find(legacy.name(), "google|new-owner"));
          // then
          assertThat(failure).isInstanceOf(ConfigurationConflictException.class);
          assertThat(provisioning.findAll())
              .singleElement()
              .satisfies(
                  user -> {
                    assertThat(user.ownerSubject()).isNull();
                    assertThat(
                            ((BasicAuthMailCredentials) user.scanConfigs().getFirst().credentials())
                                .password()
                                .getEncrypted())
                        .isEqualTo("legacy-secret");
                  });
        });
  }
}
