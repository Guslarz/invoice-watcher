package com.guslarz.invoicewatcher.dao.eventprocessing.jpa.properties;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.properties.ProcessingDaoProperties;
import com.guslarz.invoicewatcher.secret.api.Secret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Isolated JDBC persistence, bound only for the selected JPA backend.
 *
 * @param dataSource Required in jpa mode. Capability-owned JDBC connection settings.
 * @param ddlAuto Required in jpa mode. Explicit Hibernate schema policy; update is for local
 *     development.
 * @param dialect Required in jpa mode. Explicit Hibernate dialect; H2 is validated locally.
 */
@Validated
@ConfigurationProperties(ProcessingDaoProperties.Constants.PREFIX + ".jpa")
public record ProcessingJpaProperties(
    @Valid @NotNull @NestedConfigurationProperty Connection dataSource,
    @NotBlank String ddlAuto,
    @NotBlank String dialect) {
  /**
   * JDBC connection group.
   *
   * @param basic Required in jpa mode. Plain JDBC configuration.
   */
  public record Connection(@Valid @NotNull @NestedConfigurationProperty Basic basic) {}

  /**
   * JDBC credentials.
   *
   * @param url Required in jpa mode. JDBC URL; use persistent H2 for restart recovery.
   * @param username Optional in jpa mode. JDBC user name.
   * @param password Optional in jpa mode. Encrypted JDBC password; omit for local H2 defaults.
   * @param driver Required in jpa mode. JDBC driver class.
   */
  public record Basic(
      @NotBlank String url, String username, Secret password, @NotBlank String driver) {}

  @Override
  public String toString() {
    return "ProcessingJpaProperties[redacted]";
  }
}
