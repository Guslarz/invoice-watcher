package com.guslarz.invoicewatcher.mailattachment.impl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AttachmentIdentity {
  private AttachmentIdentity() {}

  public static String create(String kind, String... components) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      component(digest, kind);
      for (String value : components) {
        if (value == null || value.isBlank()) {
          throw new IllegalArgumentException("Missing attachment identity component");
        }
        component(digest, value);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  public static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static void component(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
    digest.update(bytes);
  }
}
