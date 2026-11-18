package com.guslarz.invoicewatcher.eventproducer.api;

import jakarta.validation.constraints.NotBlank;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Event producer selection.
 *
 * @param mode Required. Implementation selector for an active capability; available implementations
 *     contribute values.
 */
@Validated
@ConfigurationProperties(prefix = EventProducerProperties.Constants.PREFIX)
public record EventProducerProperties(@NotBlank String mode) {

  @UtilityClass
  public class Constants {
    public static final String PREFIX = "invoice-watcher.event-producer";
    public static final String MODE = "mode";
  }
}
