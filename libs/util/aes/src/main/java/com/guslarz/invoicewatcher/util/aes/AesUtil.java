package com.guslarz.invoicewatcher.util.aes;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AesUtil {

  public static final Random RandomGenerator = new SecureRandom();

  static final int IV_LENGTH = 12;
  static final int IV_BASE64_LENGTH = 16;
  private static final int TAG_LENGTH_BITS = 128;

  public static String encodeSecretKey(SecretKey secretKey) {
    return encode(secretKey.getEncoded());
  }

  public static SecretKey decodeSecretKey(String encodedSecretKey) {
    byte[] key = decode(encodedSecretKey);
    return toSecretKey(key);
  }

  public static SecretKey toSecretKey(byte[] key) {
    return new SecretKeySpec(key, "AES");
  }

  @SneakyThrows
  public static String encrypt(String input, SecretKey secretKey) {
    GCMParameterSpec iv = generateIv();
    Cipher cipher = getAesCipher(Cipher.ENCRYPT_MODE, secretKey, iv);
    byte[] result = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
    return encode(iv.getIV()) + encode(result);
  }

  @SneakyThrows
  public static String decrypt(String input, SecretKey secretKey) {
    if (input.length() <= IV_BASE64_LENGTH) {
      throw new IllegalArgumentException("Encrypted value is too short: expected a "
          + IV_BASE64_LENGTH + "-character IV followed by the ciphertext");
    }
    GCMParameterSpec iv = toIv(decode(input.substring(0, IV_BASE64_LENGTH)));
    String payload = input.substring(IV_BASE64_LENGTH);
    Cipher cipher = getAesCipher(Cipher.DECRYPT_MODE, secretKey, iv);
    byte[] result = cipher.doFinal(decode(payload));
    return new String(result, StandardCharsets.UTF_8);
  }

  @SneakyThrows
  private static Cipher getAesCipher(int cipherMode, SecretKey secretKey, GCMParameterSpec iv) {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(cipherMode, secretKey, iv);
    return cipher;
  }

  private static GCMParameterSpec generateIv() {
    byte[] iv = new byte[IV_LENGTH];
    RandomGenerator.nextBytes(iv);
    return toIv(iv);
  }

  private static GCMParameterSpec toIv(byte[] input) {
    return new GCMParameterSpec(TAG_LENGTH_BITS, input);
  }

  private static String encode(byte[] input) {
    return Base64.getEncoder().encodeToString(input);
  }

  private static byte[] decode(String input) {
    return Base64.getDecoder().decode(input);
  }
}
