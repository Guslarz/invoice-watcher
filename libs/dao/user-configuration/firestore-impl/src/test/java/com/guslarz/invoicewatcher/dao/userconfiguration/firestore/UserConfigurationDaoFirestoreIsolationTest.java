package com.guslarz.invoicewatcher.dao.userconfiguration.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.google.cloud.spring.data.datastore.core.DatastoreTransactionManager;
import com.google.cloud.spring.data.datastore.core.mapping.DatastoreMappingContext;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.UserConfigurationDaoProperties;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.properties.UserConfigurationDaoFirestoreProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class UserConfigurationDaoFirestoreIsolationTest {
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner().withUserConfiguration(TestApplication.class);

  @Test
  @DisplayName(
      "USER-010: disabled Firestore and its dependencies create no storage or credential beans")
  void remainsInactiveWhenNotSelected() {
    // given
    // when
    for (var runner :
        List.of(
            uut,
            uut.withPropertyValues("invoice-watcher.user-configuration.dao.mode="),
            uut.withPropertyValues("invoice-watcher.user-configuration.dao.mode=jpa"),
            uut.withPropertyValues("invoice-watcher.user-configuration.dao.mode=unknown"))) {
      runner.run(
          context -> {
            // then
            assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(UserDaoService.class)
                .doesNotHaveBean(UserConfigurationDaoProperties.class)
                .doesNotHaveBean(UserConfigurationDaoFirestoreProperties.class)
                .doesNotHaveBean(Datastore.class)
                .doesNotHaveBean(DatastoreTemplate.class)
                .doesNotHaveBean(DatastoreTransactionManager.class)
                .doesNotHaveBean(DatastoreMappingContext.class)
                .doesNotHaveBean(CredentialsProvider.class)
                .doesNotHaveBean(GcpProjectIdProvider.class);
          });
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
