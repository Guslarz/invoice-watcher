package com.guslarz.invoicewatcher.secret.aes;

import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import com.guslarz.invoicewatcher.util.aes.AesUtil;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class SecretFactoryAesImpl implements SecretFactory {

  private final SecretKey key;

  public SecretFactoryAesImpl(SecretAesProperties properties) {
    this.key = properties.key();
  }

  @Override
  public Secret fromEncrypted(String encrypted) {
    return new SecretAesImpl(encrypted, key);
  }

  @Override
  public Secret fromPlaintext(String plaintext) {
    return fromEncrypted(AesUtil.encrypt(plaintext, key));
  }
}
