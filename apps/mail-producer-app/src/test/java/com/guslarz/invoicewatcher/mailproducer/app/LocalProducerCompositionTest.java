package com.guslarz.invoicewatcher.mailproducer.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailattachment.api.service.MailAttachmentService;
import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalProducerCompositionTest {
  @TempDir Path temporary;

  @Test
  @DisplayName(
      "ATTACHMENT-012 ATTACHMENT-015: actual producer composes local filesystem and JPA without"
          + " Google clients")
  void startsWithLocalBackends() {
    // given
    var uut =
        new ApplicationContextRunner()
            .withUserConfiguration(MailProducerApp.class)
            .withPropertyValues(
                "invoice-watcher.secret.aes.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "invoice-watcher.event-producer.mode=kafka",
                "invoice-watcher.event-producer.kafka.bootstrap-servers=localhost:9092",
                "invoice-watcher.mail-producer.task.topic=all-mails",
                "invoice-watcher.user-configuration.dao.mode=jpa",
                "invoice-watcher.user-configuration.dao.jpa.ddl-auto=create-drop",
                "invoice-watcher.user-configuration.dao.jpa.dialect=org.hibernate.dialect.H2Dialect",
                "invoice-watcher.user-configuration.dao.jpa.data-source.basic.url=jdbc:h2:mem:"
                    + UUID.randomUUID(),
                "invoice-watcher.mail-attachment.dao.mode=jpa",
                "invoice-watcher.mail-attachment.dao.jpa.ddl-auto=create-drop",
                "invoice-watcher.mail-attachment.dao.jpa.dialect=org.hibernate.dialect.H2Dialect",
                "invoice-watcher.mail-attachment.dao.jpa.data-source.basic.url=jdbc:h2:mem:"
                    + UUID.randomUUID(),
                "invoice-watcher.mail-attachment.dao.jpa.data-source.basic.username=sa",
                "invoice-watcher.mail-attachment.dao.jpa.data-source.basic.driver=org.h2.Driver",
                "invoice-watcher.attachment-storage.mode=filesystem",
                "invoice-watcher.attachment-storage.filesystem.directory="
                    + temporary.resolve("objects"));
    // when
    uut.run(context -> {
      // then
      assertThat(context)
          .hasNotFailed()
          .hasSingleBean(MailProducerTask.class)
          .hasSingleBean(UserDaoService.class)
          .hasSingleBean(MailAttachmentService.class)
          .hasSingleBean(AttachmentCaptureDao.class)
          .hasSingleBean(AttachmentStorage.class);
      assertThat(context.getBeanDefinitionNames())
          .noneMatch(name ->
              name.toLowerCase().contains("datastore") || name.equals("attachmentGcsClient"));
    });
  }
}
