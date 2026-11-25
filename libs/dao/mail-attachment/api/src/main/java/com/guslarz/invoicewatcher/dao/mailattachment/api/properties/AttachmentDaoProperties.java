package com.guslarz.invoicewatcher.dao.mailattachment.api.properties;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selection of durable attachment capture persistence.
 *
 * @param mode Required. Attachment capture persistence backend when capture is active.
 */
@ConfigurationProperties(AttachmentDaoProperties.Constants.PREFIX)
public record AttachmentDaoProperties(String mode) {
  @UtilityClass
  public class Constants {
    public static final String PREFIX = "invoice-watcher.mail-attachment.dao";
    public static final String MODE = "mode";
  }
}
