package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.properties;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.UserConfigurationDaoProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Relational storage of users, active when `invoice-watcher.user-configuration.dao.mode` is `jpa`.
 *
 * @param dataSource Required in `jpa` mode. Database connection.
 * @param ddlAuto Required in `jpa` mode. Hibernate schema handling (`hibernate.hbm2ddl.auto`), e.g.
 *     `update`.
 * @param dialect Required in `jpa` mode. Hibernate dialect class, e.g. `H2Dialect`.
 */
@Validated
@ConfigurationProperties(UserConfigurationDaoProperties.Constants.PREFIX + ".jpa")
public record UserConfigurationDaoJpaProperties(
    @Valid @NestedConfigurationProperty DataSourceProperties dataSource,
    @NotBlank String ddlAuto,
    @NotBlank String dialect) {}
