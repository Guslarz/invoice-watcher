package com.guslarz.invoicewatcher.secret.aes;

import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
@ConfigurationPropertiesBinding
public class SecretFactoryAesImpl implements SecretFactory {

  private final SecretKey key;

  public SecretFactoryAesImpl(SecretAesProperties properties) {
    this.key = properties.key();
  }

  @Override
  public Secret fromEncrypted(String encrypted) {
    return new SecretAesImpl(encrypted, key);
  }
}
