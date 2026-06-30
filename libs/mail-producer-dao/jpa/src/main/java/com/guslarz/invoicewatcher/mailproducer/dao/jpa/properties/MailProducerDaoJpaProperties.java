package com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(MailProducerDaoProperties.Constants.Prefix + ".jpa")
public record MailProducerDaoJpaProperties(
    @NestedConfigurationProperty DataSourceProperties dataSource,
    String ddlAuto,
    String dialect
) {
}
