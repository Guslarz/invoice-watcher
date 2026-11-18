package com.guslarz.invoicewatcher.dao.eventprocessing.api.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Processing storage selection.
 *
 * @param mode Required. Implementation selector for an active capability; available implementations
 *     contribute values.
 */
@Validated
@ConfigurationProperties(ProcessingDaoProperties.Constants.PREFIX)
public record ProcessingDaoProperties(@NotBlank String mode) {

  @UtilityClass
  public class Constants {
    public static final String MODE = "mode";
    public static final String PREFIX = "invoice-watcher.event-processing.dao";
  }
}
