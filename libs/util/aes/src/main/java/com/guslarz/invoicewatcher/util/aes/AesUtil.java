package com.guslarz.invoicewatcher.util.aes;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AesUtil {

  private static final Random RandomGenerator = new SecureRandom();

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
    byte[] result = cipher.doFinal(input.getBytes());
    return encode(iv.getIV()) + encode(result);
  }

  @SneakyThrows
  public static String decrypt(String input, SecretKey secretKey) {
    GCMParameterSpec iv = toIv(decode(input.substring(0, 16))); // 16 characters for 12 bytes
    String payload = input.substring(16);
    Cipher cipher = getAesCipher(Cipher.DECRYPT_MODE, secretKey, iv);
    byte[] result = cipher.doFinal(decode(payload.getBytes()));
    return new String(result);
  }

  @SneakyThrows
  private static Cipher getAesCipher(int cipherMode, SecretKey secretKey, GCMParameterSpec iv) {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(cipherMode, secretKey, iv);
    return cipher;
  }

  private static GCMParameterSpec generateIv() {
    byte[] iv = new byte[12];
    RandomGenerator.nextBytes(iv);
    return toIv(iv);
  }

  private static GCMParameterSpec toIv(byte[] input) {
    return new GCMParameterSpec(128, input);
  }

  private static String encode(byte[] input) {
    return Base64.getEncoder().encodeToString(input);
  }

  private static byte[] decode(byte[] input) {
    return Base64.getDecoder().decode(input);
  }

  private static byte[] decode(String input) {
    return decode(input.getBytes());
  }
}
