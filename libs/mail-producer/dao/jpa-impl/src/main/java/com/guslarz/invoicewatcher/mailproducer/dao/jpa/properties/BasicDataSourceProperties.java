package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import jakarta.validation.constraints.NotBlank;

/**
 * Plain JDBC data source.
 *
 * @param url Required when `basic` is set. JDBC URL, e.g. `jdbc:h2:mem:invoice-watcher`.
 * @param username Optional. Database username.
 * @param password Optional. Database password.
 * @param driver Optional. JDBC driver class, e.g. `org.h2.Driver`.
 */

public record BasicDataSourceProperties(
    @NotBlank String url,
    String username,
    String password,
    String driver
) {
}
