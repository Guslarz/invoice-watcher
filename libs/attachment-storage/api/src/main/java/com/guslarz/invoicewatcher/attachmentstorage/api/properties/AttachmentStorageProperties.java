package com.guslarz.invoicewatcher.attachmentstorage.api.properties;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selection of private immutable attachment storage.
 *
 * @param mode Required. Private attachment storage backend when capture is active.
 */
@ConfigurationProperties(AttachmentStorageProperties.Constants.PREFIX)
public record AttachmentStorageProperties(String mode) {
  @UtilityClass
  public class Constants {
    public static final String PREFIX = "invoice-watcher.attachment-storage";
    public static final String MODE = "mode";
  }
}
