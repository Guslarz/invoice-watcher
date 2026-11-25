package com.guslarz.invoicewatcher.attachmentstorage.gcs.properties;

import com.guslarz.invoicewatcher.attachmentstorage.api.properties.AttachmentStorageProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Private evidence storage; no public or arbitrary endpoint support.
 *
 * @param bucket Required in gcs mode. Dedicated private attachment bucket.
 * @param storeId Optional in gcs mode. Logical allowlisted store identifier carried in events.
 * @param operationTimeout Optional in gcs mode. Finite deadline for storage operations.
 */
@Validated
@ConfigurationProperties(AttachmentStorageProperties.Constants.PREFIX + ".gcs")
public record AttachmentGcsProperties(
    @NotBlank String bucket,
    @DefaultValue("mail-attachments") @Pattern(regexp = "[a-z0-9-]{1,64}") String storeId,
    @DefaultValue("15s") Duration operationTimeout) {}
