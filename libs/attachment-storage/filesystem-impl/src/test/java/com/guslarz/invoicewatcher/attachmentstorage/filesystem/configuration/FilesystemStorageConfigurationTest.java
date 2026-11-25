package com.guslarz.invoicewatcher.attachmentstorage.filesystem.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FilesystemStorageConfigurationTest {
  @TempDir private Path temporary;

  @Test
  @DisplayName(
      "ATTACHMENT-012: filesystem selection initializes no GCS client on the shared classpath")
  void selectsOnlyFilesystemStorage() {
    // given
    Path directory = temporary.resolve("objects");
    var uut = new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
    // when
    uut.withPropertyValues(
            "invoice-watcher.attachment-storage.mode=filesystem",
            "invoice-watcher.attachment-storage.filesystem.directory=" + directory)
        .run(context -> {
          // then
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(AttachmentStorage.class)
              .doesNotHaveBean("attachmentGcsClient");
          assertThat(directory).isDirectory();
        });
    // when
    uut.withPropertyValues(
            "invoice-watcher.attachment-storage.mode=unknown",
            "invoice-watcher.attachment-storage.filesystem.directory="
                + temporary.resolve("inactive"))
        .run(context -> {
          // then
          assertThat(context).hasNotFailed().doesNotHaveBean(AttachmentStorage.class);
          assertThat(temporary.resolve("inactive")).doesNotExist();
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
