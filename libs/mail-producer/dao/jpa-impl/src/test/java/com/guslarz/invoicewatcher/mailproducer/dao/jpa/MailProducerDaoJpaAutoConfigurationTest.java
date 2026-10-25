package com.guslarz.invoicewatcher.mailproducer.dao.jpa;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailServer;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailProducerDaoJpaAutoConfigurationTest {

  private static final String Dao = "invoice-watcher.mail-producer.dao.";
  private static final String Jpa = Dao + "jpa.";

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          HibernateJpaAutoConfiguration.class,
          TransactionAutoConfiguration.class,
          MailProducerDaoJpaAutoConfiguration.class
      ))
      .withBean(SecretFactory.class, MailProducerDaoJpaAutoConfigurationTest::secretFactory)
      .withPropertyValues(
          Dao + "mode=jpa",
          Jpa + "ddl-auto=create",
          Jpa + "dialect=org.hibernate.dialect.H2Dialect",
          Jpa + "data-source.basic.url=jdbc:h2:mem:" + UUID.randomUUID(),
          Jpa + "data-source.basic.driver=org.h2.Driver"
      );

  private static SecretFactory secretFactory() {
    SecretFactory factory = mock(SecretFactory.class);
    when(factory.fromEncrypted(anyString())).thenAnswer(invocation -> secret(invocation.getArgument(0)));
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
        .scanConfigs(List.of(MailScanConfig.builder()
            .server(MailServer.builder().host(host).port(993).build())
            .credentials(BasicAuthMailCredentials.builder()
                .username("jan@example.com")
                .password(secret(password))
                .build())
            .folders(folders.stream()
                .map(name -> FolderConfig.builder().name(name).startDate(startDate).build())
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
    runner.run(context -> {
      UserDaoService userDaoService = context.getBean(UserDaoService.class);
      userDaoService.upsertUserByName(mockUser("old.example.com", "old", List.of("INBOX", "Invoices")));
      userDaoService.upsertUserByName(mockUser("new.example.com", "new", List.of("Archive")));

      // then
      List<User> users = userDaoService.findAll();
      assertThat(users).singleElement().satisfies(user -> {
        assertThat(user.name()).isEqualTo("jan@example.com");
        assertThat(user.scanConfigs()).singleElement().satisfies(scanConfig -> {
          assertThat(scanConfig.server().host()).isEqualTo("new.example.com");
          assertThat(((BasicAuthMailCredentials) scanConfig.credentials()).password().getEncrypted())
              .isEqualTo("new");
          assertThat(scanConfig.folders()).extracting(FolderConfig::name).containsExactly("Archive");
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
  @DisplayName("stores all credentials in one table with a type discriminator and a required server")
  void generatesSingleCredentialsTableAndRequiredServer() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {
      context.getBean(UserDaoService.class)
          .upsertUserByName(mockUser("imap.example.com", "secret", List.of("INBOX")));

      // then
      JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
      assertThat(jdbc.queryForList(
          "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
              + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'"
              + " AND TABLE_NAME LIKE '%CREDENTIALS%'",
          String.class))
          .containsExactly("MAIL_CREDENTIALS_ENTITY");
      assertThat(jdbc.queryForList("SELECT type FROM mail_credentials_entity", String.class))
          .containsExactly("basic");
      assertThat(jdbc.queryForList(
          "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
              + " WHERE TABLE_NAME = 'MAIL_SCAN_CONFIG_ENTITY' AND IS_NULLABLE = 'NO'",
          String.class))
          .contains("HOST", "PORT");
    });
  }
}
