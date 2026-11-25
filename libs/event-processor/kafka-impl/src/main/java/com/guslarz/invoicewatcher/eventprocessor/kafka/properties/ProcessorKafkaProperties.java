package com.guslarz.invoicewatcher.eventprocessor.kafka.properties;

import com.guslarz.invoicewatcher.eventprocessor.api.properties.ProcessorProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka processor transport; required only when a stage is registered.
 *
 * @param bootstrapServers Required. Broker endpoints for processor consumers and publication.
 * @param groupPrefix Optional. Prefix for independent stage consumer groups.
 * @param offsetReset Required. Explicit new-group behavior: earliest, latest or none.
 * @param quarantineRetentionMs Optional. Required time-based quarantine recovery window in
 *     milliseconds.
 * @param maxPublicationBytes Optional. Upper bound including Kafka record overhead for publication.
 * @param inputRetentionMs Optional. Required input-topic time retention in milliseconds.
 * @param resultRetentionMs Optional. Required result-topic time retention in milliseconds.
 * @param replayRetentionMs Optional. Required replay-topic time retention in milliseconds.
 * @param concurrency Optional. Consumer threads per stage; retries pause the affected thread's
 *     assignments.
 */
@Builder(toBuilder = true)
@Validated
@ConfigurationProperties(ProcessorKafkaProperties.Constants.PREFIX)
public record ProcessorKafkaProperties(
    @NotBlank String bootstrapServers,
    @DefaultValue(Defaults.GROUP_PREFIX) @Pattern(regexp = "[A-Za-z0-9._-]{1,100}")
        String groupPrefix,
    @NotBlank @Pattern(regexp = "earliest|latest|none") String offsetReset,
    @DefaultValue(Defaults.RETENTION_MS) @Min(1) long quarantineRetentionMs,
    @DefaultValue(Defaults.MAX_PUBLICATION_BYTES) @Min(16384) @Max(16777216)
        int maxPublicationBytes,
    @DefaultValue(Defaults.CONCURRENCY) @Min(1) @Max(64) int concurrency,
    @DefaultValue(Defaults.RETENTION_MS) @Min(1) long inputRetentionMs,
    @DefaultValue(Defaults.RETENTION_MS) @Min(1) long resultRetentionMs,
    @DefaultValue(Defaults.RETENTION_MS) @Min(1) long replayRetentionMs) {
  @UtilityClass
  public class Constants {
    public static final String PREFIX = ProcessorProperties.Constants.PREFIX + ".kafka";
  }

  @UtilityClass
  public class Defaults {
    public static final String GROUP_PREFIX = "invoice-processor-";
    public static final String RETENTION_MS = "604800000";
    public static final String MAX_PUBLICATION_BYTES = "4194304";
    public static final String CONCURRENCY = "1";
  }
}
