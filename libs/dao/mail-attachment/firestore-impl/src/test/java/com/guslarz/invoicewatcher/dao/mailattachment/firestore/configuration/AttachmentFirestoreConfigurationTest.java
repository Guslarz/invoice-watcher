package com.guslarz.invoicewatcher.dao.mailattachment.firestore.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.datastore.Datastore;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AttachmentFirestoreConfigurationTest {
  @Test
  @DisplayName("ATTACHMENT-006: disabled DAO creates no client and active DAO owns its namespace")
  void isolatesInactiveAndSelectedProviderGraphs() {
    // given
    var uut = new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
    // when
    uut.run(context -> {
      // then
      assertThat(context)
          .doesNotHaveBean(Datastore.class)
          .doesNotHaveBean(AttachmentCaptureDao.class);
    });
    // when
    uut.withPropertyValues(
            "invoice-watcher.mail-attachment.dao.mode=firestore",
            "invoice-watcher.mail-attachment.dao.firestore.project-id=synthetic-project",
            "invoice-watcher.mail-attachment.dao.firestore.host=http://127.0.0.1:8081")
        .run(context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(AttachmentCaptureDao.class);
          assertThat(
                  context
                      .getBean("attachmentDatastore", Datastore.class)
                      .getOptions()
                      .getNamespace())
              .isEqualTo("mail-attachments");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
