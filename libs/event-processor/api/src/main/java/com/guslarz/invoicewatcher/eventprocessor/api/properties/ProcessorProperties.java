package com.guslarz.invoicewatcher.eventprocessor.api.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Shared record processing limits.
 *
 * @param maxAttempts Optional. Total admitted handler attempts, including the initial attempt.
 * @param retryDelays Optional. Base delays for retries, with twenty percent bounded jitter.
 * @param handlerTimeout Optional. Maximum handler execution time.
 * @param lease Optional. Exclusive claim lifetime, longer than the handler timeout.
 * @param recoveryInterval Optional. Delay before rechecking unavailable shared infrastructure.
 * @param outboxInterval Optional. Interval between durable pending-output recovery passes.
 * @param outboxBatch Optional. Maximum pending outcomes handled in a recovery pass.
 * @param maxResultBytes Optional. Total serialized result bytes allowed per processing outcome.
 */
@Validated
@ConfigurationProperties(ProcessorProperties.Constants.PREFIX)
public record ProcessorProperties(
    @DefaultValue(Defaults.MAX_ATTEMPTS) @Min(1) @Max(20) int maxAttempts,
    @DefaultValue({Defaults.RETRY_FIRST, Defaults.RETRY_SECOND, Defaults.RETRY_THIRD}) @NotNull
        List<Duration> retryDelays,
    @DefaultValue(Defaults.HANDLER_TIMEOUT) @NotNull Duration handlerTimeout,
    @DefaultValue(Defaults.LEASE) @NotNull Duration lease,
    @DefaultValue(Defaults.RECOVERY_INTERVAL) @NotNull Duration recoveryInterval,
    @DefaultValue(Defaults.OUTBOX_INTERVAL) @NotNull Duration outboxInterval,
    @DefaultValue(Defaults.OUTBOX_BATCH) @Min(1) @Max(1000) int outboxBatch,
    @DefaultValue(Defaults.MAX_RESULT_BYTES) @Min(1) @Max(524288) int maxResultBytes) {
  public ProcessorProperties {
    retryDelays = List.copyOf(retryDelays);
  }

  @AssertTrue(message = "Invalid processor timing configuration")
  public boolean isTimingValid() {
    return !retryDelays.isEmpty()
        && retryDelays.stream().allMatch(d -> d.toMillis() > 0)
        && handlerTimeout.toMillis() > 0
        && lease.compareTo(handlerTimeout) > 0
        && recoveryInterval.toMillis() > 0
        && outboxInterval.toMillis() > 0;
  }

  @UtilityClass
  public class Constants {
    public static final String PREFIX = "invoice-watcher.event-processor";
  }

  @UtilityClass
  public class Defaults {
    public static final String MAX_ATTEMPTS = "4";
    public static final String HANDLER_TIMEOUT = "30s";
    public static final String LEASE = "120s";
    public static final String OUTBOX_INTERVAL = "5s";
    public static final String OUTBOX_BATCH = "100";
    public static final String MAX_RESULT_BYTES = "524288";
    public static final String RETRY_FIRST = "1s";
    public static final String RETRY_SECOND = "5s";
    public static final String RETRY_THIRD = "30s";
    public static final String RECOVERY_INTERVAL = "30s";
  }
}
