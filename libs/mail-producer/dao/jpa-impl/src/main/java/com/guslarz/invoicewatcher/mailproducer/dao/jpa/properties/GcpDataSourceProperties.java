package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import jakarta.validation.constraints.NotBlank;

/**
 * Cloud SQL for PostgreSQL instance, reached with IAM database authentication as the service
 * account of the Google default credentials (no username or password).
 *
 * @param instanceConnectionName Required when `gcp` is set. Instance connection name,
 *     `project:region:instance`.
 * @param databaseName Required when `gcp` is set. Database name in the instance.
 */

public record GcpDataSourceProperties(
    @NotBlank String instanceConnectionName,
    @NotBlank String databaseName
) {
}
