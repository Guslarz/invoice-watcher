package com.guslarz.invoicewatcher.secret.aes;

import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.util.aes.AesUtil;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;

@RequiredArgsConstructor
public class SecretAesImpl implements Secret {

  private final String encryptedValue;
  private final SecretKey secretKey;

  @Override
  public String getEncrypted() {
    return encryptedValue;
  }

  @Override
  public String getDecrypted() {
    return AesUtil.decrypt(encryptedValue, secretKey);
  }
}
