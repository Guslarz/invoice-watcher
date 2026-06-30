package com.guslarz.invoicewatcher.mailproducer.dao.api;

import lombok.experimental.UtilityClass;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = MailProducerDaoProperties.Constants.Prefix)
public record MailProducerDaoProperties(
    String mode
) {

  @UtilityClass
  public class Constants {
    public final String Prefix = "invoice-watcher.mail-producer.dao";
    public final String Mode = "mode";
  }
}
