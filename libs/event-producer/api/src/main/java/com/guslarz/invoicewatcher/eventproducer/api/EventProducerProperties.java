package com.guslarz.invoicewatcher.eventproducer.api;

import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Event producer selection.
 *
 * @param mode Required. Event backend; the only supported value is `kafka`.
 */
@Validated
@ConfigurationProperties(prefix = EventProducerProperties.Constants.Prefix)
public record EventProducerProperties(
    @NotNull Mode mode
) {

  /** Supported values of {@code mode}. */
  public enum Mode {
    KAFKA
  }

  @UtilityClass
  public class Constants {
    public static final String Prefix = "invoice-watcher.event-producer";
    public static final String Mode = "mode";
  }
}
