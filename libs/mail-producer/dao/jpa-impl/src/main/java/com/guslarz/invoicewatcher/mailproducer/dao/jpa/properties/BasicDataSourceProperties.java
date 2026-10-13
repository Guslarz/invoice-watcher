package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

/**
 * Plain JDBC data source.
 *
 * @param url JDBC URL, e.g. `jdbc:h2:mem:invoice-watcher`.
 * @param username Database username.
 * @param password Database password.
 * @param driver JDBC driver class, e.g. `org.h2.Driver`.
 */

public record BasicDataSourceProperties(
    String url,
    String username,
    String password,
    String driver
) {
}
