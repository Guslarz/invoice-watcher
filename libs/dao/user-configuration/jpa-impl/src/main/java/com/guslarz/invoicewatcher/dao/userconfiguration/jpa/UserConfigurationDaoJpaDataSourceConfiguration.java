package com.guslarz.invoicewatcher.dao.userconfiguration.jpa;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.UserConfigurationDaoProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties.BasicDataSourceProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties.DataSourceProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties.GcpDataSourceProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties.UserConfigurationDaoJpaProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class UserConfigurationDaoJpaDataSourceConfiguration {

  private static final String DATA_SOURCE_PREFIX =
      UserConfigurationDaoProperties.Constants.PREFIX + ".jpa.data-source";

  private static final String SERVICE_ACCOUNT_SUFFIX = ".gserviceaccount.com";

  @Bean(UserConfigurationDaoJpaAutoConfiguration.Beans.DATA_SOURCE)
  DataSource dataSource(
      UserConfigurationDaoJpaProperties properties,
      @Qualifier("userConfigurationJpaCredentials")
          ObjectProvider<CredentialsProvider> credentialsProvider)
      throws IOException {
    DataSourceProperties dataSource = properties.dataSource();
    BasicDataSourceProperties basic = dataSource == null ? null : dataSource.basic();
    GcpDataSourceProperties gcp = dataSource == null ? null : dataSource.gcp();
    requireExactlyOne(basic, gcp);
    if (basic != null) {
      return basicDataSource(basic);
    }
    CredentialsProvider credentials = credentialsProvider.getObject();
    return cloudSqlDataSource(gcp, iamDatabaseUser(credentials.getCredentials()));
  }

  private void requireExactlyOne(BasicDataSourceProperties basic, GcpDataSourceProperties gcp) {
    boolean basicSet = basic != null;
    boolean gcpSet = gcp != null;
    boolean neitherSet = !basicSet && !gcpSet;
    boolean bothSet = basicSet && gcpSet;
    if (neitherSet || bothSet) {
      throw new IllegalStateException(
          "Exactly one of %s.basic and %s.gcp must be set"
              .formatted(DATA_SOURCE_PREFIX, DATA_SOURCE_PREFIX));
    }
  }

  private String iamDatabaseUser(Credentials credentials) {
    if (!(credentials instanceof ServiceAccountSigner serviceAccount)) {
      throw new IllegalStateException(
          "Cloud SQL IAM database authentication needs service account credentials, got %s"
              .formatted(credentials.getClass().getName()));
    }
    String account = serviceAccount.getAccount();
    return account.endsWith(SERVICE_ACCOUNT_SUFFIX)
        ? account.substring(0, account.length() - SERVICE_ACCOUNT_SUFFIX.length())
        : account;
  }

  private DataSource basicDataSource(BasicDataSourceProperties basic) {
    var dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(basic.url());
    dataSource.setUsername(basic.username());
    dataSource.setPassword(basic.password());
    if (basic.driver() != null) {
      dataSource.setDriverClassName(basic.driver());
    }
    return dataSource;
  }

  private DataSource cloudSqlDataSource(GcpDataSourceProperties gcp, String username) {
    var dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(
        "jdbc:postgresql://google/"
            + encode(gcp.databaseName())
            + "?cloudSqlInstance="
            + encode(gcp.instanceConnectionName())
            + "&socketFactory=com.google.cloud.sql.postgres.SocketFactory&enableIamAuth=true");
    dataSource.setUsername(username);
    dataSource.setDriverClassName("org.postgresql.Driver");
    return dataSource;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("%3A", ":");
  }

  @Bean("userConfigurationJpaCredentials")
  @ConditionalOnMissingBean(name = "userConfigurationJpaCredentials")
  @ConditionalOnProperty(prefix = DATA_SOURCE_PREFIX + ".gcp", name = "instance-connection-name")
  CredentialsProvider credentialsProvider() {
    return GoogleCredentials::getApplicationDefault;
  }
}
