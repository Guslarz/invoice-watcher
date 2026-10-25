package com.guslarz.invoicewatcher.mailproducer.dao.api;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage of users and their mailbox configuration.
 *
 * @param mode Required. Storage backend: `firestore` or `jpa`.
 */
@ConfigurationProperties(prefix = MailProducerDaoProperties.Constants.Prefix)
public record MailProducerDaoProperties(
    String mode
) {

  @UtilityClass
  public class Constants {
    public static final String Prefix = "invoice-watcher.mail-producer.dao";
    public static final String Mode = "mode";
  }
}
