package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Relational storage of users, active when `invoice-watcher.mail-producer.dao.mode` is `jpa`.
 *
 * @param dataSource Required in `jpa` mode. Database connection.
 * @param ddlAuto Required in `jpa` mode. Hibernate schema handling (`hibernate.hbm2ddl.auto`),
 *     e.g. `update`.
 * @param dialect Required in `jpa` mode. Hibernate dialect class, e.g.
 *     `org.hibernate.dialect.H2Dialect`.
 */
@Validated
@ConfigurationProperties(MailProducerDaoProperties.Constants.Prefix + ".jpa")
public record MailProducerDaoJpaProperties(
    @Valid @NestedConfigurationProperty DataSourceProperties dataSource,
    @NotBlank String ddlAuto,
    @NotBlank String dialect
) {
}
