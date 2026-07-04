package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public record DataSourceProperties(
    @NestedConfigurationProperty BasicDataSourceProperties basic,
    @NestedConfigurationProperty GcpDataSourceProperties gcp
) {
}
