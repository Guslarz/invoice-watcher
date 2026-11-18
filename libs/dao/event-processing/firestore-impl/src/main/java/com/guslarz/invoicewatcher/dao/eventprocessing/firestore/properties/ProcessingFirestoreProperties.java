package com.guslarz.invoicewatcher.dao.eventprocessing.firestore.properties;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.properties.ProcessingDaoProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Isolated processing storage in Firestore in Datastore mode.
 *
 * @param projectId Required. Google Cloud project, or synthetic emulator project.
 * @param databaseId Optional. Datastore database identifier.
 * @param namespace Optional. Namespace isolating processing state from user configuration.
 * @param host Optional. Explicit local Datastore emulator endpoint; omitting uses Google
 *     credentials.
 */
@Validated
@ConfigurationProperties(ProcessingFirestoreProperties.Constants.PREFIX)
public record ProcessingFirestoreProperties(
    @NotBlank String projectId,
    @DefaultValue(Defaults.DATABASE_ID) @NotBlank String databaseId,
    @DefaultValue(Defaults.NAMESPACE) @NotBlank String namespace,
    String host) {
  @UtilityClass
  public class Constants {
    public static final String PREFIX = ProcessingDaoProperties.Constants.PREFIX + ".firestore";
  }

  @UtilityClass
  public class Defaults {
    public static final String DATABASE_ID = "(default)";
    public static final String NAMESPACE = "event-processing";
  }
}
