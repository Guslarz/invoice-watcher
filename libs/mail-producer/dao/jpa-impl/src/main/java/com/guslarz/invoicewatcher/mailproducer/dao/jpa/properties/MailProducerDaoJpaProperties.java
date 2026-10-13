package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Relational storage of users, active when `invoice-watcher.mail-producer.dao.mode` is `jpa`.
 *
 * @param dataSource Database connection.
 * @param ddlAuto Hibernate schema handling (`hibernate.hbm2ddl.auto`), e.g. `update`.
 * @param dialect Hibernate dialect class, e.g. `org.hibernate.dialect.H2Dialect`.
 */
@ConfigurationProperties(MailProducerDaoProperties.Constants.Prefix + ".jpa")
public record MailProducerDaoJpaProperties(
    @NestedConfigurationProperty DataSourceProperties dataSource,
    String ddlAuto,
    String dialect
) {
}
