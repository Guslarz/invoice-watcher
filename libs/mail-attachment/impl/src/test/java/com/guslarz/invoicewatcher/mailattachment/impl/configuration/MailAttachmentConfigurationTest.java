package com.guslarz.invoicewatcher.mailattachment.impl.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.mailattachment.api.service.MailAttachmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MailAttachmentConfigurationTest {
  @Test
  @DisplayName("ATTACHMENT-003 ATTACHMENT-008: binds defaults and rejects unsafe limits at startup")
  void validatesCapturePolicyWithNormalDiscovery() {
    // given
    var uut =
        new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withBean(AttachmentStorage.class, () -> mock(AttachmentStorage.class))
            .withBean(AttachmentCaptureDao.class, () -> mock(AttachmentCaptureDao.class));
    // when
    uut.run(context -> {
      // then
      assertThat(context).hasNotFailed().hasSingleBean(MailAttachmentService.class);
    });
    // when
    uut.withPropertyValues("invoice-watcher.mail-attachment.evidence-lifetime=7d")
        .run(context -> {
          // then
          assertThat(context).hasFailed();
        });
    // when
    uut.withPropertyValues("invoice-watcher.mail-attachment.max-attachment-bytes=0")
        .run(context -> {
          // then
          assertThat(context).hasFailed();
        });
  }

  @Test
  @DisplayName("ATTACHMENT-005 ATTACHMENT-006: active capture requires explicit storage and DAO")
  void rejectsMissingCaptureBackends() {
    // given
    var uut = new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
    // when
    uut.run(context -> {
      // then
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .hasStackTraceContaining(AttachmentStorage.class.getName());
    });
    // when
    uut.withBean(AttachmentStorage.class, () -> mock(AttachmentStorage.class))
        .run(context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining(AttachmentCaptureDao.class.getName());
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
