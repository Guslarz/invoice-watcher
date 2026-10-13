package com.guslarz.invoicewatcher.eventproducer.api;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Event producer selection.
 *
 * @param mode Event backend; the only supported value is `kafka`.
 */
@ConfigurationProperties(prefix = EventProducerProperties.Constants.Prefix)
public record EventProducerProperties(
    String mode
) {

  @UtilityClass
  public class Constants {
    public static final String Prefix = "invoice-watcher.event-producer";
    public static final String Mode = "mode";
  }
}
