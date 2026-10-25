package com.guslarz.invoicewatcher.mailproducer.dao.jpa;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.MailProducerDaoJpaProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailProducerDaoJpaDataSourceConfigurationTest {

  private static final String Basic = "invoice-watcher.mail-producer.dao.jpa.data-source.basic.";
  private static final String Gcp = "invoice-watcher.mail-producer.dao.jpa.data-source.gcp.";
  private static final String Jpa = "invoice-watcher.mail-producer.dao.jpa.";

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withUserConfiguration(
          PropertiesConfiguration.class,
          MailProducerDaoJpaDataSourceConfiguration.class
      )
      .withPropertyValues(
          Jpa + "ddl-auto=none",
          Jpa + "dialect=org.hibernate.dialect.H2Dialect"
      );

  private static List<ConfigurationPropertyName> invalidProperties(Throwable startupFailure) {
    BindValidationException exception =
        (BindValidationException) NestedExceptionUtils.getRootCause(startupFailure);
    String prefix = Objects.requireNonNull(exception).getValidationErrors().getName().toString();
    return exception.getValidationErrors().getAllErrors().stream()
        .map(error -> ConfigurationPropertyName.adapt(
            prefix + "." + ((FieldError) error).getField(), '.'))
        .toList();
  }

  private static CredentialsProvider serviceAccount(String email) {
    ComputeEngineCredentials credentials = mock(ComputeEngineCredentials.class);
    when(credentials.getAccount()).thenReturn(email);
    return () -> credentials;
  }

  @Test
  @DisplayName("USER-013: connects to Cloud SQL as the default credentials' service account when gcp is set")
  void connectsToCloudSqlWhenGcpIsSet() {
    // given
    ApplicationContextRunner runner = uut
        .withBean(CredentialsProvider.class,
            () -> serviceAccount("app-sa@proj.iam.gserviceaccount.com"))
        .withPropertyValues(
            Gcp + "instance-connection-name=p:r:i",
            Gcp + "database-name=iw"
        );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      HikariDataSource dataSource = (HikariDataSource) context.getBean(DataSource.class);
      assertThat(dataSource.getDriverClassName()).isEqualTo("org.postgresql.Driver");
      assertThat(dataSource.getJdbcUrl())
          .startsWith("jdbc:postgresql://google/iw?")
          .contains("socketFactory=com.google.cloud.sql.postgres.SocketFactory")
          .contains("cloudSqlInstance=p:r:i")
          .contains("enableIamAuth=true");
      assertThat(dataSource.getUsername()).isEqualTo("app-sa@proj.iam");
      assertThat(dataSource.getPassword()).isNull();
    });
  }

  @Test
  @DisplayName("USER-013: uses the basic JDBC data source when basic is set")
  void usesBasicDataSourceWhenBasicIsSet() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Basic + "url=jdbc:h2:mem:invoice-watcher",
        Basic + "username=sa",
        Basic + "password=secret",
        Basic + "driver=org.h2.Driver"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      HikariDataSource dataSource = (HikariDataSource) context.getBean(DataSource.class);
      assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:h2:mem:invoice-watcher");
      assertThat(dataSource.getDriverClassName()).isEqualTo("org.h2.Driver");
      assertThat(dataSource.getUsername()).isEqualTo("sa");
      assertThat(dataSource.getPassword()).isEqualTo("secret");
    });
  }

  @Test
  @DisplayName("USER-013: fails to start when neither basic nor gcp is set")
  void failsWhenNeitherIsSet() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .rootCause()
          .hasMessageContaining("data-source.basic")
          .hasMessageContaining("data-source.gcp");
    });
  }

  @Test
  @DisplayName("USER-013: fails to start when both basic and gcp are set")
  void failsWhenBothAreSet() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Basic + "url=jdbc:h2:mem:invoice-watcher",
        Basic + "driver=org.h2.Driver",
        Gcp + "instance-connection-name=p:r:i",
        Gcp + "database-name=iw"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .rootCause()
          .hasMessageContaining("data-source.basic")
          .hasMessageContaining("data-source.gcp");
    });
  }

  @Test
  @DisplayName("USER-013: fails to start when gcp is set and the default credentials are not a service account")
  void failsWhenCredentialsAreNotServiceAccount() {
    // given
    CredentialsProvider userCredentials = () -> mock(GoogleCredentials.class);
    ApplicationContextRunner runner = uut
        .withBean(CredentialsProvider.class, () -> userCredentials)
        .withPropertyValues(
            Gcp + "instance-connection-name=p:r:i",
            Gcp + "database-name=iw"
        );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .rootCause()
          .hasMessageContaining("service account");
    });
  }

  @Test
  @DisplayName("USER-013: fails to start without ddl-auto")
  void failsWithoutDdlAuto() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Jpa + "ddl-auto=",
        Basic + "url=jdbc:h2:mem:invoice-watcher"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of(Jpa + "ddl-auto"));
    });
  }

  @Test
  @DisplayName("USER-013: fails to start when basic is set without url")
  void failsWhenBasicIsSetWithoutUrl() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Basic + "driver=org.h2.Driver"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of(Basic + "url"));
    });
  }

  @Test
  @DisplayName("USER-013: fails to start when gcp is set without database name")
  void failsWhenGcpIsSetWithoutDatabaseName() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Gcp + "instance-connection-name=p:r:i"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of(Gcp + "database-name"));
    });
  }

  @EnableConfigurationProperties(MailProducerDaoJpaProperties.class)
  static class PropertiesConfiguration {
  }
}
