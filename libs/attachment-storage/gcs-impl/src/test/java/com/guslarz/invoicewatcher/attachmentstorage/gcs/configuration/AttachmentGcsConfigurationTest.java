package com.guslarz.invoicewatcher.attachmentstorage.gcs.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AttachmentGcsConfigurationTest {
  @Test
  @DisplayName(
      "ATTACHMENT-005: an inactive GCS implementation never initializes credentials or clients")
  void isolatesInactiveGcs() {
    // given
    var uut = new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
    // when
    uut.run(context -> {
      // then
      assertThat(context).hasNotFailed().doesNotHaveBean(Storage.class);
    });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
