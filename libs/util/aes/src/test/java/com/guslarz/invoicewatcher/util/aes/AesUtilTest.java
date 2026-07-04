package com.guslarz.invoicewatcher.util.aes;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;

class AesUtilTest {

  @Test
  void testEncryptAndDecrypt() {
    // given
    String input = "dummyTextąść:/";
    SecretKey secretKey = AesUtil.decodeSecretKey("4yWK+SIr9BkkB3ymrJ1A1mtQLH7opt27zItE7akvJ9w=");

    // when
    String encrypted = AesUtil.encrypt(input, secretKey);
    String decrypted = AesUtil.decrypt(encrypted, secretKey);

    // then
    assertThat(decrypted).isEqualTo(input);
  }
}