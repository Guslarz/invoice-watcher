package com.guslarz.invoicewatcher.mailproducer.dao.jpa;

import com.google.cloud.spring.autoconfigure.sql.CloudSqlJdbcInfoProvider;
import com.google.cloud.spring.autoconfigure.sql.DatabaseType;
import com.google.cloud.spring.autoconfigure.sql.DefaultCloudSqlJdbcInfoProvider;
import com.google.cloud.spring.autoconfigure.sql.GcpCloudSqlProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.MailCredentialsMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.MailScanConfigMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.GcpDataSourceProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.MailProducerDaoJpaProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.service.FolderConfigDaoServiceJpaImpl;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.service.UserDaoServiceJpaImpl;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.util.DummyCloudSqlJdbcInfoProvider;
import jakarta.annotation.PostConstruct;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@AutoConfiguration
@Import({
    UserDaoServiceJpaImpl.class,
    FolderConfigDaoServiceJpaImpl.class,
    UserMapper.class,
    MailCredentialsMapper.class,
    MailScanConfigMapper.class
})
@EnableConfigurationProperties(MailProducerDaoJpaProperties.class)
@ConditionalOnProperty(
    prefix = MailProducerDaoProperties.Constants.Prefix,
    name = MailProducerDaoProperties.Constants.Mode,
    havingValue = "jpa"
)
@EnableJpaRepositories(
    entityManagerFactoryRef = MailProducerDaoJpaAutoConfiguration.Beans.EntityManagerFactory,
    transactionManagerRef = MailProducerDaoJpaAutoConfiguration.Beans.TransactionManager
)
public class MailProducerDaoJpaAutoConfiguration {

  @PostConstruct
  public void init() {
    log.info("Configuring mail producer dao for jpa");
  }

  @Bean(Beans.EntityManagerFactory)
  LocalContainerEntityManagerFactoryBean entityManager(
      @Qualifier(Beans.DataSource) DataSource dataSource,
      EntityManagerFactoryBuilder builder,
      MailProducerDaoJpaProperties properties) {
    return builder
        .dataSource(dataSource)
        .packages(getClass().getPackageName())
        .properties(Map.of(
            "hibernate.hbm2ddl.auto", properties.ddlAuto(),
            "hibernate.dialect", properties.dialect()
        ))
        .build();
  }

  @Bean(Beans.TransactionManager)
  PlatformTransactionManager transactionManager(
      @Qualifier(Beans.EntityManagerFactory) LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
    return new JpaTransactionManager(Objects.requireNonNull(
        entityManagerFactoryBean.getObject()
    ));
  }

  @Bean(Beans.DataSource)
  DataSource dataSource(MailProducerDaoJpaProperties properties) {
    // TODO more than basic
    return DataSourceBuilder
        .create()
        .url(properties
            .dataSource()
            .basic()
            .url())
        .username(properties
            .dataSource()
            .basic()
            .username())
        .password(properties
            .dataSource()
            .basic()
            .password())
        .driverClassName(properties
            .dataSource()
            .basic()
            .driver())
        .build();
  }

  @Bean(Beans.InfoProvider)
  CloudSqlJdbcInfoProvider cloudSqlJdbcInfoProvider(MailProducerDaoJpaProperties properties) {
    return Optional
        .ofNullable(properties
            .dataSource()
            .gcp())
        .map(this::toCloudSqlJdbcInfoProvider)
        .orElseGet(() -> new DummyCloudSqlJdbcInfoProvider(properties));
  }

  private CloudSqlJdbcInfoProvider toCloudSqlJdbcInfoProvider(GcpDataSourceProperties properties) {
    GcpCloudSqlProperties gcpProperties = new GcpCloudSqlProperties();
    gcpProperties.setInstanceConnectionName(properties.instanceConnectionName());
    gcpProperties.setDatabaseName(properties.databaseName());
    return new DefaultCloudSqlJdbcInfoProvider(
        gcpProperties,
        DatabaseType.POSTGRESQL
    );
  }

  @UtilityClass
  class Beans {
    static final String DataSource = "mailProducerDaoJpaDataSource";
    static final String EntityManagerFactory = "mailProducerDaoJpaEntityManagerFactory";
    static final String TransactionManager = "mailProducerDaoJpaTransactionManager";
    static final String InfoProvider = "mailProducerDaoJpaInfoProvider";
  }
}
