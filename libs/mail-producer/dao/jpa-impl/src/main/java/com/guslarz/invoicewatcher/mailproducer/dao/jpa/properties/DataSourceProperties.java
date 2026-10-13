package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import org.springframework.boot.context.properties.NestedConfigurationProperty;
/**
 * Database connection: a plain JDBC data source or a Cloud SQL instance.
 *
 * @param basic Plain JDBC data source.
 * @param gcp Cloud SQL for PostgreSQL instance; when set, used instead of `basic`.
 */

public record DataSourceProperties(
    @NestedConfigurationProperty BasicDataSourceProperties basic,
    @NestedConfigurationProperty GcpDataSourceProperties gcp
) {
}
