package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

/**
 * Cloud SQL for PostgreSQL instance.
 *
 * @param instanceConnectionName Instance connection name, `project:region:instance`.
 * @param databaseName Database name in the instance.
 */

public record GcpDataSourceProperties(
    String instanceConnectionName,
    String databaseName
) {
}
