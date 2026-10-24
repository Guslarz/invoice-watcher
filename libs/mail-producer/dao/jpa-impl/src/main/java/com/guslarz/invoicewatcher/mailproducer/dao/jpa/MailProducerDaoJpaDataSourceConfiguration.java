package com.guslarz.invoicewatcher.mailproducer.dao.jpa;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spring.autoconfigure.sql.CloudSqlJdbcInfoProvider;
import com.google.cloud.spring.autoconfigure.sql.DatabaseType;
import com.google.cloud.spring.autoconfigure.sql.DefaultCloudSqlJdbcInfoProvider;
import com.google.cloud.spring.autoconfigure.sql.GcpCloudSqlProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.BasicDataSourceProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.DataSourceProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.GcpDataSourceProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.MailProducerDaoJpaProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class MailProducerDaoJpaDataSourceConfiguration {

  private static final String DataSourcePrefix =
      MailProducerDaoProperties.Constants.Prefix + ".jpa.data-source";

  private static final String ServiceAccountSuffix = ".gserviceaccount.com";

  @Bean(MailProducerDaoJpaAutoConfiguration.Beans.DataSource)
  DataSource dataSource(
      MailProducerDaoJpaProperties properties,
      ObjectProvider<CredentialsProvider> credentialsProvider) throws IOException {
    DataSourceProperties dataSource = properties.dataSource();
    BasicDataSourceProperties basic = dataSource == null ? null : dataSource.basic();
    GcpDataSourceProperties gcp = dataSource == null ? null : dataSource.gcp();
    requireExactlyOne(basic, gcp);
    if (basic != null) {
      return basicDataSource(basic);
    }
    CredentialsProvider credentials =
        credentialsProvider.getIfUnique(() -> GoogleCredentials::getApplicationDefault);
    return cloudSqlDataSource(gcp, iamDatabaseUser(credentials.getCredentials()));
  }

  private void requireExactlyOne(BasicDataSourceProperties basic, GcpDataSourceProperties gcp) {
    boolean basicSet = basic != null;
    boolean gcpSet = gcp != null;
    boolean neitherSet = !basicSet && !gcpSet;
    boolean bothSet = basicSet && gcpSet;
    if (neitherSet || bothSet) {
      throw new IllegalStateException("Exactly one of %s.basic and %s.gcp must be set"
          .formatted(DataSourcePrefix, DataSourcePrefix));
    }
  }

  private String iamDatabaseUser(Credentials credentials) {
    if (!(credentials instanceof ServiceAccountSigner serviceAccount)) {
      throw new IllegalStateException(
          "Cloud SQL IAM database authentication needs service account credentials, got %s"
              .formatted(credentials.getClass().getName()));
    }
    String account = serviceAccount.getAccount();
    return account.endsWith(ServiceAccountSuffix)
        ? account.substring(0, account.length() - ServiceAccountSuffix.length())
        : account;
  }

  private DataSource basicDataSource(BasicDataSourceProperties basic) {
    return DataSourceBuilder
        .create()
        .url(basic.url())
        .username(basic.username())
        .password(basic.password())
        .driverClassName(basic.driver())
        .build();
  }

  private DataSource cloudSqlDataSource(GcpDataSourceProperties gcp, String username) {
    GcpCloudSqlProperties gcpProperties = new GcpCloudSqlProperties();
    gcpProperties.setInstanceConnectionName(gcp.instanceConnectionName());
    gcpProperties.setDatabaseName(gcp.databaseName());
    gcpProperties.setEnableIamAuth(true);
    CloudSqlJdbcInfoProvider infoProvider = new DefaultCloudSqlJdbcInfoProvider(
        gcpProperties,
        DatabaseType.POSTGRESQL
    );
    return DataSourceBuilder
        .create()
        .url(infoProvider.getJdbcUrl())
        .username(username)
        .driverClassName(infoProvider.getJdbcDriverClass())
        .build();
  }
}
