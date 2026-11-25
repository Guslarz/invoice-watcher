package com.guslarz.invoicewatcher.attachmentstorage.gcs.configuration;

import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.guslarz.invoicewatcher.attachmentstorage.api.properties.AttachmentStorageProperties;
import com.guslarz.invoicewatcher.attachmentstorage.gcs.properties.AttachmentGcsProperties;
import com.guslarz.invoicewatcher.attachmentstorage.gcs.service.GcsAttachmentStorage;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = AttachmentStorageProperties.Constants.PREFIX,
    name = AttachmentStorageProperties.Constants.MODE,
    havingValue = "gcs")
@EnableConfigurationProperties(AttachmentGcsProperties.class)
@Import(GcsAttachmentStorage.class)
public class AttachmentGcsAutoConfiguration {
  @Bean("attachmentGcsClient")
  Storage attachmentGcsClient(AttachmentGcsProperties properties) {
    Duration timeout = properties.operationTimeout();
    if (timeout == null || !timeout.isPositive() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
      throw new IllegalArgumentException("Invalid attachment-storage.gcs.operation-timeout");
    }
    var retry =
        StorageOptions.getDefaultRetrySettings().toBuilder()
            .setTotalTimeoutDuration(timeout)
            .setInitialRpcTimeoutDuration(timeout)
            .setMaxRpcTimeoutDuration(timeout)
            .build();
    var transport =
        HttpTransportOptions.newBuilder()
            .setConnectTimeout(Math.toIntExact(timeout.toMillis()))
            .setReadTimeout(Math.toIntExact(timeout.toMillis()))
            .build();
    return StorageOptions.newBuilder()
        .setRetrySettings(retry)
        .setTransportOptions(transport)
        .build()
        .getService();
  }
}
