package com.guslarz.invoicewatcher.dao.eventprocessing.firestore.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.datastore.Datastore;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

class ProcessingFirestoreConfigurationTest {
  @Test
  @DisplayName(
      "PROCESSOR-001: initial DAO implementation uses an isolated namespace and an explicit"
          + " emulator")
  void composesFirestoreBehindDaoApi() {
    // given
    assertThat(
            ClassUtils.isPresent(
                "com.guslarz.invoicewatcher.dao.userconfiguration.firestore."
                    + "UserConfigurationDaoFirestoreAutoConfiguration",
                getClass().getClassLoader()))
        .isFalse();
    var uut =
        new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                "invoice-watcher.event-processing.dao.mode=firestore",
                "invoice-watcher.event-processing.dao.firestore.project-id=synthetic-project",
                "invoice-watcher.event-processing.dao.firestore.host=http://127.0.0.1:8081");
    // when
    uut.run(
        context -> {
          // then
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(ProcessingDao.class)
              .hasSingleBean(Datastore.class);
          assertThat(
                  context
                      .getBean("processingDatastore", Datastore.class)
                      .getOptions()
                      .getNamespace())
              .isEqualTo("event-processing");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
