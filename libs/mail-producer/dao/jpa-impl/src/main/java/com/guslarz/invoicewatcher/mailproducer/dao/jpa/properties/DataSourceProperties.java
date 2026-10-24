package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import org.springframework.boot.context.properties.NestedConfigurationProperty;
/**
 * Database connection: a plain JDBC data source or a Cloud SQL instance. Exactly one of
 * `basic` and `gcp` must be set.
 *
 * @param basic Plain JDBC data source.
 * @param gcp Cloud SQL for PostgreSQL instance with IAM database authentication.
 */

public record DataSourceProperties(
    @NestedConfigurationProperty BasicDataSourceProperties basic,
    @NestedConfigurationProperty GcpDataSourceProperties gcp
) {
}
