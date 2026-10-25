package com.guslarz.invoicewatcher.mailconnector.impl;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MailConnectionFactoryImpl.class)
@EnableConfigurationProperties(MailConnectorImapProperties.class)
public class MailConnectorAutoConfiguration {
}
