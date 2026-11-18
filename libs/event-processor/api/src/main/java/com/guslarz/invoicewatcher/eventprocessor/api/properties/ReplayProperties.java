package com.guslarz.invoicewatcher.eventprocessor.api.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds on explicit replay requests; registered stages provide the topic/version allowlist.
 *
 * @param maxRecords Optional. Maximum records selected by one request.
 * @param maxRate Optional. Maximum replay publications per second.
 */
@Validated
@ConfigurationProperties(ReplayProperties.Constants.PREFIX)
public record ReplayProperties(
    @DefaultValue(Defaults.MAX_RECORDS) @Min(1) @Max(10000) int maxRecords,
    @DefaultValue(Defaults.MAX_RATE) @DecimalMin("0.01") @DecimalMax("1000") double maxRate) {
  @UtilityClass
  public class Constants {
    public static final String PREFIX = ProcessorProperties.Constants.PREFIX + ".replay";
  }

  @UtilityClass
  public class Defaults {
    public static final String MAX_RECORDS = "100";
    public static final String MAX_RATE = "1";
  }
}
