package com.guslarz.invoicewatcher.dao.userconfiguration.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.api.gax.core.CredentialsProvider;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.UserConfigurationDaoProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties.UserConfigurationDaoJpaProperties;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

class UserConfigurationDaoJpaIsolationTest {
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner().withUserConfiguration(TestApplication.class);

  @Test
  @DisplayName("USER-010: disabled JPA and its dependencies create no storage infrastructure")
  void remainsInactiveWhenNotSelected() {
    // given
    // when
    for (var runner :
        List.of(
            uut,
            uut.withPropertyValues("invoice-watcher.user-configuration.dao.mode="),
            uut.withPropertyValues("invoice-watcher.user-configuration.dao.mode=firestore"),
            uut.withPropertyValues("invoice-watcher.user-configuration.dao.mode=unknown"))) {
      runner.run(
          context -> {
            // then
            assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(UserDaoService.class)
                .doesNotHaveBean(UserConfigurationDaoProperties.class)
                .doesNotHaveBean(UserConfigurationDaoJpaProperties.class)
                .doesNotHaveBean(DataSource.class)
                .doesNotHaveBean(EntityManagerFactory.class)
                .doesNotHaveBean(PlatformTransactionManager.class)
                .doesNotHaveBean(CredentialsProvider.class);
          });
    }
  }

  @Test
  @DisplayName(
      "REG-015 USER-010 USER-013: discovered JPA starts independently with local H2 storage")
  void startsWithOwnGraph() {
    // given
    var runner =
        uut.withBean(SecretFactory.class, () -> mock(SecretFactory.class))
            .withPropertyValues(
                "invoice-watcher.user-configuration.dao.mode=jpa",
                "invoice-watcher.user-configuration.dao.jpa.ddl-auto=create-drop",
                "invoice-watcher.user-configuration.dao.jpa.dialect=org.hibernate.dialect.H2Dialect",
                "invoice-watcher.user-configuration.dao.jpa.data-source.basic.url=jdbc:h2:mem:"
                    + UUID.randomUUID());
    // when
    runner.run(
        context -> {
          // then
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(UserDaoService.class)
              .hasSingleBean(RegistrationDao.class)
              .hasSingleBean(DataSource.class)
              .hasSingleBean(EntityManagerFactory.class)
              .hasSingleBean(PlatformTransactionManager.class)
              .doesNotHaveBean(CredentialsProvider.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
