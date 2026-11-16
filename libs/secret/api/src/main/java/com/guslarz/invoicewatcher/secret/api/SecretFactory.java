package com.guslarz.invoicewatcher.secret.api;

public interface SecretFactory {

  Secret fromEncrypted(String encrypted);

  Secret fromPlaintext(String plaintext);
}
