package com.guslarz.invoicewatcher.dao.userconfiguration.jpa;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.UserConfigurationDaoProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper.MailCredentialsMapper;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper.MailScanConfigMapper;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties.UserConfigurationDaoJpaProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service.FolderConfigDaoServiceJpaImpl;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service.RegistrationDaoJpaImpl;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.service.UserDaoServiceJpaImpl;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Slf4j
@AutoConfiguration
@EnableTransactionManagement
@Import({
  UserConfigurationDaoJpaDataSourceConfiguration.class,
  UserDaoServiceJpaImpl.class,
  RegistrationDaoJpaImpl.class,
  FolderConfigDaoServiceJpaImpl.class,
  UserMapper.class,
  MailCredentialsMapper.class,
  MailScanConfigMapper.class
})
@EnableConfigurationProperties(UserConfigurationDaoJpaProperties.class)
@ConditionalOnProperty(
    prefix = UserConfigurationDaoProperties.Constants.PREFIX,
    name = UserConfigurationDaoProperties.Constants.MODE,
    havingValue = "jpa")
@EnableJpaRepositories(
    entityManagerFactoryRef = UserConfigurationDaoJpaAutoConfiguration.Beans.ENTITY_MANAGER_FACTORY,
    transactionManagerRef = UserConfigurationDaoJpaAutoConfiguration.Beans.TRANSACTION_MANAGER)
public class UserConfigurationDaoJpaAutoConfiguration {

  @PostConstruct
  public void init() {
    log.info("Configuring shared user configuration DAO for jpa");
  }

  @Bean(Beans.ENTITY_MANAGER_FACTORY)
  LocalContainerEntityManagerFactoryBean entityManager(
      @Qualifier(Beans.DATA_SOURCE) DataSource dataSource,
      UserConfigurationDaoJpaProperties properties) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan(getClass().getPackageName());
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(
        Map.of(
            "hibernate.hbm2ddl.auto",
            properties.ddlAuto(),
            "hibernate.dialect",
            properties.dialect(),
            "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
            "hibernate.implicit_naming_strategy",
            JpaNamingStrategy.class.getName()));
    return factory;
  }

  @Bean(Beans.TRANSACTION_MANAGER)
  PlatformTransactionManager transactionManager(
      @Qualifier(Beans.ENTITY_MANAGER_FACTORY)
          LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
    return new JpaTransactionManager(Objects.requireNonNull(entityManagerFactoryBean.getObject()));
  }

  @UtilityClass
  class Beans {
    static final String DATA_SOURCE = "mailProducerDaoJpaDataSource";
    static final String ENTITY_MANAGER_FACTORY = "mailProducerDaoJpaEntityManagerFactory";
    static final String TRANSACTION_MANAGER = "mailProducerDaoJpaTransactionManager";
  }
}
