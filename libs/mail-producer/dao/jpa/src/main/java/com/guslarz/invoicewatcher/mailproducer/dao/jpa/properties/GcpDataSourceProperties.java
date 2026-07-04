package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

public record GcpDataSourceProperties(
    String instanceConnectionName,
    String databaseName
) {
}
