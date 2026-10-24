package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

/**
 * Cloud SQL for PostgreSQL instance, reached with IAM database authentication as the service
 * account of the Google default credentials (no username or password).
 *
 * @param instanceConnectionName Instance connection name, `project:region:instance`.
 * @param databaseName Database name in the instance.
 */

public record GcpDataSourceProperties(
    String instanceConnectionName,
    String databaseName
) {
}
