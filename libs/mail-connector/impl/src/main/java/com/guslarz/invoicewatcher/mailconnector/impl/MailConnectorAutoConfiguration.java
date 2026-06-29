package com.guslarz.invoicewatcher.mailconnector.impl;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
@AutoConfiguration
@EnableConfigurationProperties(MailConnectorProperties.class)
public class MailConnectorAutoConfiguration {
}
