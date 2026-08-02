package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.secret.api.Secret;
import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("invoice-watcher.mail-producer.mock.user")
public record MailProducerMockUserProperties(
    Boolean enabled,
    String host,
    @DefaultValue(Defaults.Port) int port,
    String email,
    Secret password,
    List<String> folders
) {

  @UtilityClass
  class Defaults {
    static final String Port = "993";
  }
}
