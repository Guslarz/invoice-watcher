package com.guslarz.invoicewatcher.dao.mailattachment.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

class JpaBackendIsolationTest {
  @TempDir Path temporary;
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner().withUserConfiguration(TestApplication.class);

  @Test
  @DisplayName("ATTACHMENT-015 PROCESSOR-017: inactive SQL backends bind no properties or clients")
  void remainsInactive() {
    // given
    // when
    uut.run(context -> {
      // then
      assertThat(context)
          .hasNotFailed()
          .doesNotHaveBean(DataSource.class)
          .doesNotHaveBean(EntityManagerFactory.class)
          .doesNotHaveBean(PlatformTransactionManager.class)
          .doesNotHaveBean(AttachmentCaptureDao.class)
          .doesNotHaveBean(ProcessingDao.class);
    });
  }

  @Test
  @DisplayName(
      "ATTACHMENT-012 ATTACHMENT-015 PROCESSOR-017: filesystem and three JPA graphs coexist without"
          + " cloud clients")
  void composesIndependentLocalBackends() {
    // given
    var properties = new ArrayList<String>();
    for (String area : List.of("user-configuration", "mail-attachment", "event-processing")) {
      properties.addAll(
          List.of(
              "invoice-watcher." + area + ".dao.mode=jpa",
              "invoice-watcher." + area + ".dao.jpa.ddl-auto=create-drop",
              "invoice-watcher." + area + ".dao.jpa.dialect=org.hibernate.dialect.H2Dialect",
              "invoice-watcher."
                  + area
                  + ".dao.jpa.data-source.basic.url=jdbc:h2:mem:"
                  + UUID.randomUUID(),
              "invoice-watcher." + area + ".dao.jpa.data-source.basic.username=sa",
              "invoice-watcher." + area + ".dao.jpa.data-source.basic.driver=org.h2.Driver"));
    }
    properties.add("invoice-watcher.attachment-storage.mode=filesystem");
    properties.add(
        "invoice-watcher.attachment-storage.filesystem.directory=" + temporary.resolve("objects"));
    // when
    uut.withBean(SecretFactory.class, () -> mock(SecretFactory.class))
        .withPropertyValues(properties.toArray(String[]::new))
        .run(context -> {
          // then
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(UserDaoService.class)
              .hasSingleBean(AttachmentCaptureDao.class)
              .hasSingleBean(ProcessingDao.class)
              .hasSingleBean(AttachmentStorage.class);
          assertThat(context.getBeansOfType(DataSource.class)).hasSize(3);
          assertThat(context.getBeansOfType(EntityManagerFactory.class)).hasSize(3);
          assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(3);
          assertThat(context.getBeanDefinitionNames())
              .noneMatch(name ->
                  name.toLowerCase().contains("datastore") || name.equals("attachmentGcsClient"));
          var captureFactory =
              context.getBean("attachmentJpaEntityManagerFactory", EntityManagerFactory.class);
          assertThat(captureFactory.getMetamodel().getEntities())
              .allSatisfy(entity ->
                  assertThat(entity.getJavaType().getPackageName())
                      .contains("mailattachment.jpa.entity"));
        });
  }

  @Test
  @DisplayName(
      "ATTACHMENT-015 PROCESSOR-017: selected JPA fails visibly without owned connection settings")
  void rejectsMissingSettings() {
    // given
    // when
    for (String area : List.of("mail-attachment", "event-processing")) {
      uut.withPropertyValues("invoice-watcher." + area + ".dao.mode=jpa")
          .run(context -> {
            // then
            assertThat(context).hasFailed();
          });
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
