package com.guslarz.invoicewatcher.attachmentstorage.filesystem.properties;

import com.guslarz.invoicewatcher.attachmentstorage.api.properties.AttachmentStorageProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.file.Path;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Private filesystem storage, bound only in filesystem mode.
 *
 * @param directory Required in filesystem mode. Absolute dedicated private POSIX directory.
 * @param storeId Optional in filesystem mode. Logical store identifier in evidence references.
 * @param cleanupInterval Optional in filesystem mode. Interval between local expiry cleanup runs.
 */
@Validated
@ConfigurationProperties(AttachmentStorageProperties.Constants.PREFIX + ".filesystem")
public record FilesystemStorageProperties(
    @NotNull Path directory,
    @DefaultValue(Defaults.STORE_ID) @Pattern(regexp = "[a-z0-9-]{1,64}") String storeId,
    @DefaultValue(Defaults.CLEANUP_INTERVAL) @NotNull @DurationMin(seconds = 1)
        Duration cleanupInterval) {
  @UtilityClass
  public class Defaults {
    public static final String STORE_ID = "local-attachments";
    public static final String CLEANUP_INTERVAL = "60s";
  }
}
