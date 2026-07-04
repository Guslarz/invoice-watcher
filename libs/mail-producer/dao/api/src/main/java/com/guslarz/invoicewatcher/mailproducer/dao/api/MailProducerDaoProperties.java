package com.guslarz.invoicewatcher.mailproducer.dao.api;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = MailProducerDaoProperties.Constants.Prefix)
public record MailProducerDaoProperties(
    String mode
) {

  @UtilityClass
  public class Constants {
    public static final String Prefix = "invoice-watcher.mail-producer.dao";
    public static final String Mode = "mode";
  }
}
