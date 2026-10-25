package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
/**
 * Database connection: a plain JDBC data source or a Cloud SQL instance. Exactly one of
 * `basic` and `gcp` must be set.
 *
 * @param basic Required unless `gcp` is set. Plain JDBC data source.
 * @param gcp Required unless `basic` is set. Cloud SQL for PostgreSQL instance with IAM database
 *     authentication.
 */

public record DataSourceProperties(
    @Valid @NestedConfigurationProperty BasicDataSourceProperties basic,
    @Valid @NestedConfigurationProperty GcpDataSourceProperties gcp
) {
}
