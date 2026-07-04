package com.guslarz.invoicewatcher.secret.aes;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;

// Only used to generate metadata file
@ConfigurationProperties("invoice-watcher.secret.aes")
public record SecretAesProperties(
  SecretKey key
) {
}
