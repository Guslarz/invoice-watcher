package com.guslarz.invoicewatcher.attachmentstorage.filesystem.configuration;

import com.guslarz.invoicewatcher.attachmentstorage.api.properties.AttachmentStorageProperties;
import com.guslarz.invoicewatcher.attachmentstorage.filesystem.properties.FilesystemStorageProperties;
import com.guslarz.invoicewatcher.attachmentstorage.filesystem.service.FilesystemAttachmentStorage;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = AttachmentStorageProperties.Constants.PREFIX,
    name = AttachmentStorageProperties.Constants.MODE,
    havingValue = "filesystem")
@EnableConfigurationProperties(FilesystemStorageProperties.class)
public class FilesystemStorageAutoConfiguration {
  @Bean(destroyMethod = "close")
  FilesystemAttachmentStorage filesystemAttachmentStorage(FilesystemStorageProperties properties) {
    return new FilesystemAttachmentStorage(properties, Clock.systemUTC());
  }
}
