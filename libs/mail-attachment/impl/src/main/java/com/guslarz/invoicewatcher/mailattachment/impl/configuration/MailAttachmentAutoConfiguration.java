package com.guslarz.invoicewatcher.mailattachment.impl.configuration;

import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.mailattachment.api.properties.MailAttachmentProperties;
import com.guslarz.invoicewatcher.mailattachment.impl.service.DefaultMailAttachmentService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.annotation.Validated;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(MailAttachmentProperties.class)
@Validated
public class MailAttachmentAutoConfiguration {
  @Bean
  DefaultMailAttachmentService mailAttachmentService(
      MailAttachmentProperties properties,
      AttachmentStorage storage,
      AttachmentCaptureDao dao,
      ObjectProvider<MeterRegistry> meters) {
    if (!properties.isConsistent()) {
      throw new IllegalArgumentException("Invalid invoice-watcher.mail-attachment limits");
    }
    return new DefaultMailAttachmentService(
        properties,
        storage,
        dao,
        Clock.systemUTC(),
        meters.getIfAvailable(SimpleMeterRegistry::new));
  }
}
