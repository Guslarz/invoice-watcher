package com.guslarz.invoicewatcher.mailattachment.api.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded untrusted PDF evidence capture.
 *
 * @param maxAttachmentBytes Optional. Maximum decoded bytes in one stored attachment.
 * @param maxTotalBytes Optional. Maximum decoded attachment bytes examined in one message.
 * @param maxCandidates Optional. Maximum attachment candidates enumerated in one message.
 * @param maxMimeDepth Optional. Maximum MIME nesting depth.
 * @param maxManifestBytes Optional. Maximum serialized attachment manifest bytes.
 * @param captureTimeout Optional. Maximum elapsed time for capture; stream operations are bounded.
 * @param evidenceLifetime Optional. Evidence lifetime measured from first durable reservation.
 * @param supportedReplayHorizon Optional. Supported input topic replay window; no quarantine
 *     extension.
 * @param recoveryMargin Optional. Additional processing and recovery time after the replay window.
 * @param sendTimeout Optional. Upper bound reserved for each Kafka publication attempt.
 */
@Validated
@ConfigurationProperties("invoice-watcher.mail-attachment")
public record MailAttachmentProperties(
    @DefaultValue("10485760") @Min(1) @Max(16777216) int maxAttachmentBytes,
    @DefaultValue("26214400") @Min(1) @Max(67108864) int maxTotalBytes,
    @DefaultValue("20") @Min(1) @Max(1000) int maxCandidates,
    @DefaultValue("20") @Min(1) @Max(100) int maxMimeDepth,
    @DefaultValue("65536") @Min(1024) @Max(524288) int maxManifestBytes,
    @DefaultValue("60s") @NotNull Duration captureTimeout,
    @DefaultValue("9d") @NotNull Duration evidenceLifetime,
    @DefaultValue("7d") @NotNull Duration supportedReplayHorizon,
    @DefaultValue("1d") @NotNull Duration recoveryMargin,
    @DefaultValue("30s") @NotNull Duration sendTimeout) {
  @AssertTrue(
      message = "attachment timing and size limits must provide a finite publication window")
  public boolean isConsistent() {
    return captureTimeout != null
        && evidenceLifetime != null
        && supportedReplayHorizon != null
        && recoveryMargin != null
        && sendTimeout != null
        && captureTimeout.isPositive()
        && supportedReplayHorizon.isPositive()
        && recoveryMargin.isPositive()
        && sendTimeout.compareTo(Duration.ofSeconds(30)) >= 0
        && captureTimeout.compareTo(Duration.ofMinutes(5)) <= 0
        && maxTotalBytes >= maxAttachmentBytes
        && evidenceLifetime.compareTo(
                supportedReplayHorizon.plus(recoveryMargin).plus(sendTimeout).plus(captureTimeout))
            > 0;
  }
}
