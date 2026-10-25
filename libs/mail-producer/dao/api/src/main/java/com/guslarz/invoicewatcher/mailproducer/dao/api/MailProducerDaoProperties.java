package com.guslarz.invoicewatcher.mailproducer.dao.api;

import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Storage of users and their mailbox configuration.
 *
 * @param mode Required. Storage backend: `firestore` or `jpa`.
 */
@Validated
@ConfigurationProperties(prefix = MailProducerDaoProperties.Constants.Prefix)
public record MailProducerDaoProperties(
    @NotNull Mode mode
) {

  /** Supported values of {@code mode}. */
  public enum Mode {
    FIRESTORE,
    JPA
  }

  @UtilityClass
  public class Constants {
    public static final String Prefix = "invoice-watcher.mail-producer.dao";
    public static final String Mode = "mode";
  }
}
