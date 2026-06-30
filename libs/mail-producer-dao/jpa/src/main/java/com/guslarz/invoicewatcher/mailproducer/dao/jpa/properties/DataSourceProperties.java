package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

public record DataSourceProperties(
    BasicDataSourceProperties basic,
    GcpDataSourceProperties gcp
) {
}
