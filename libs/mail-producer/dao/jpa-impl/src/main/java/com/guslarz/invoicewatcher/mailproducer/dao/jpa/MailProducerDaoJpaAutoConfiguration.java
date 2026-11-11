package com.guslarz.invoicewatcher.mailproducer.dao.jpa;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.MailCredentialsMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.MailScanConfigMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.mapper.UserMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.MailProducerDaoJpaProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.service.FolderConfigDaoServiceJpaImpl;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.service.UserDaoServiceJpaImpl;
import jakarta.annotation.PostConstruct;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

@Slf4j
@AutoConfiguration
@Import({
    MailProducerDaoJpaDataSourceConfiguration.class,
    UserDaoServiceJpaImpl.class,
    FolderConfigDaoServiceJpaImpl.class,
    UserMapper.class,
    MailCredentialsMapper.class,
    MailScanConfigMapper.class
})
@EnableConfigurationProperties(MailProducerDaoJpaProperties.class)
@ConditionalOnProperty(
    prefix = MailProducerDaoProperties.Constants.PREFIX,
    name = MailProducerDaoProperties.Constants.MODE,
    havingValue = "jpa"
)
@EnableJpaRepositories(
    entityManagerFactoryRef = MailProducerDaoJpaAutoConfiguration.Beans.ENTITY_MANAGER_FACTORY,
    transactionManagerRef = MailProducerDaoJpaAutoConfiguration.Beans.TRANSACTION_MANAGER
)
public class MailProducerDaoJpaAutoConfiguration {

  @PostConstruct
  public void init() {
    log.info("Configuring mail producer dao for jpa");
  }

  @Bean(Beans.ENTITY_MANAGER_FACTORY)
  LocalContainerEntityManagerFactoryBean entityManager(
      @Qualifier(Beans.DATA_SOURCE) DataSource dataSource,
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

  @Bean(Beans.TRANSACTION_MANAGER)
  PlatformTransactionManager transactionManager(
      @Qualifier(Beans.ENTITY_MANAGER_FACTORY) LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
    return new JpaTransactionManager(Objects.requireNonNull(
        entityManagerFactoryBean.getObject()
    ));
  }

  @UtilityClass
  class Beans {
    static final String DATA_SOURCE = "mailProducerDaoJpaDataSource";
    static final String ENTITY_MANAGER_FACTORY = "mailProducerDaoJpaEntityManagerFactory";
    static final String TRANSACTION_MANAGER = "mailProducerDaoJpaTransactionManager";
  }
}
