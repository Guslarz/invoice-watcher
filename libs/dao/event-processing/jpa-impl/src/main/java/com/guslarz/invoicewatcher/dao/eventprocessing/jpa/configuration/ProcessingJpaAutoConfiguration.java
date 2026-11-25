package com.guslarz.invoicewatcher.dao.eventprocessing.jpa.configuration;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.properties.ProcessingDaoProperties;
import com.guslarz.invoicewatcher.dao.eventprocessing.jpa.properties.ProcessingJpaProperties;
import com.guslarz.invoicewatcher.dao.eventprocessing.jpa.service.JpaProcessingDao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = ProcessingDaoProperties.Constants.PREFIX,
    name = ProcessingDaoProperties.Constants.MODE,
    havingValue = "jpa")
@EnableConfigurationProperties(ProcessingJpaProperties.class)
@Import(JpaProcessingDao.class)
public class ProcessingJpaAutoConfiguration {
  @Bean(name = Beans.DATA_SOURCE, destroyMethod = "close")
  HikariDataSource dataSource(ProcessingJpaProperties properties) {
    var basic = properties.dataSource().basic();
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(basic.url());
    config.setDriverClassName(basic.driver());
    if (basic.username() != null) {
      config.setUsername(basic.username());
    }
    if (basic.password() != null) {
      config.setPassword(basic.password().getDecrypted());
    }
    config.setMaximumPoolSize(4);
    config.setConnectionTimeout(1000);
    config.setPoolName("processingJpa");
    return new HikariDataSource(config);
  }

  @Bean(Beans.ENTITY_MANAGER_FACTORY)
  LocalContainerEntityManagerFactoryBean entityManagerFactory(
      @Qualifier(Beans.DATA_SOURCE) HikariDataSource dataSource,
      ProcessingJpaProperties properties) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setPersistenceUnitName("processingJpa");
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.guslarz.invoicewatcher.dao.eventprocessing.jpa.entity");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(
        Map.of(
            "hibernate.hbm2ddl.auto",
            properties.ddlAuto(),
            "hibernate.dialect",
            properties.dialect(),
            "jakarta.persistence.lock.timeout",
            1000));
    return factory;
  }

  @Bean(Beans.TRANSACTION_MANAGER)
  PlatformTransactionManager transactionManager(
      @Qualifier(Beans.ENTITY_MANAGER_FACTORY) EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }

  @UtilityClass
  public class Beans {
    public static final String DATA_SOURCE = "processingJpaDataSource";
    public static final String ENTITY_MANAGER_FACTORY = "processingJpaEntityManagerFactory";
    public static final String TRANSACTION_MANAGER = "processingJpaTransactionManager";
  }
}
