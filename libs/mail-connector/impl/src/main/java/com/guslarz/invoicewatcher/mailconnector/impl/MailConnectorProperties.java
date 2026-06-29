package com.guslarz.invoicewatcher.mailconnector.impl;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("invoice-watcher.mail")
public record MailConnectorProperties(
    String host,
    @DefaultValue(Defaults.Port) int port,
    String username,
    String password
) {

  @UtilityClass
  class Defaults {
    final String Port = "993";
  }
}
