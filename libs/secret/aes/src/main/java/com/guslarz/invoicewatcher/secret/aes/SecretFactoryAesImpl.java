package com.guslarz.invoicewatcher.secret.aes;

import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import com.guslarz.invoicewatcher.util.aes.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
@ConfigurationPropertiesBinding
public class SecretFactoryAesImpl implements SecretFactory {

  private final SecretKey key;

  public SecretFactoryAesImpl(@Value("${invoice-watcher.secret.aes.key}") String key) {
    this.key = AesUtil.decodeSecretKey(key);
  }

  @Override
  public Secret fromEncrypted(String encrypted) {
    return new SecretAesImpl(encrypted, key);
  }
}
