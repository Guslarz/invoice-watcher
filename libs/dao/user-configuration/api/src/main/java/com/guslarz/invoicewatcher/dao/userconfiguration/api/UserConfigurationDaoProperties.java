package com.guslarz.invoicewatcher.dao.userconfiguration.api;

import jakarta.validation.constraints.NotBlank;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Storage of users and their mailbox configuration.
 *
 * @param mode Required. Implementation selector for an active capability; available implementations
 *     contribute values.
 */
@Validated
@ConfigurationProperties(prefix = UserConfigurationDaoProperties.Constants.PREFIX)
public record UserConfigurationDaoProperties(@NotBlank String mode) {

  @UtilityClass
  public class Constants {
    public static final String PREFIX = "invoice-watcher.user-configuration.dao";
    public static final String MODE = "mode";
  }
}
