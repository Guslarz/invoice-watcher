package com.guslarz.invoicewatcher.mailconnector.impl;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MailConnectionFactoryImpl.class)
public class MailConnectorAutoConfiguration {
}
