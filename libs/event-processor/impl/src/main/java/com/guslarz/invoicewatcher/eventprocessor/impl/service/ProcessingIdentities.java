package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.experimental.UtilityClass;

@UtilityClass
class ProcessingIdentities {

  static String hash(String prefix, String... components) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      component(digest, prefix);
      for (String value : components) {
        component(digest, value);
      }
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static void component(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
    digest.update((byte) ':');
    digest.update(bytes);
  }
}
