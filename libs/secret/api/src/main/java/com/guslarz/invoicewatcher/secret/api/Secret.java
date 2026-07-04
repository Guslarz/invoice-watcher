package com.guslarz.invoicewatcher.secret.api;

public interface Secret {

  String getEncrypted();

  String getDecrypted();
}
