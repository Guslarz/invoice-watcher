package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

public record BasicDataSourceProperties(
    String url,
    String username,
    String password,
    String driver
) {
}
