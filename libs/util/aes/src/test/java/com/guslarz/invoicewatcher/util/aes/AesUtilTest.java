package com.guslarz.invoicewatcher.util.aes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesUtilTest {

  private static final SecretKey KEY =
      AesUtil.decodeSecretKey("4yWK+SIr9BkkB3ymrJ1A1mtQLH7opt27zItE7akvJ9w=");
  private static final SecretKey OTHER_KEY =
      AesUtil.decodeSecretKey("Qk9Hk2m1xv8n0pYt3cR7sLw5aZ9eJ4uF6hD2gB8iK0M=");

  @Test
  void testEncryptAndDecrypt() {
    // given
    String input = "dummyTextąść:/";

    // when
    String encrypted = AesUtil.encrypt(input, KEY);
    String decrypted = AesUtil.decrypt(encrypted, KEY);

    // then
    assertThat(decrypted).isEqualTo(input);
  }

  @Test
  @DisplayName("SECRET-001: the same input encrypted twice gives two different values")
  void encryptUsesFreshIv() {
    // given
    String input = "password";

    // when
    String first = AesUtil.encrypt(input, KEY);
    String second = AesUtil.encrypt(input, KEY);

    // then
    assertThat(first).isNotEqualTo(second);
    assertThat(AesUtil.decrypt(first, KEY)).isEqualTo(input);
    assertThat(AesUtil.decrypt(second, KEY)).isEqualTo(input);
  }

  @Test
  @DisplayName("SECRET-002: an encrypted value starts with the Base64 IV")
  void encryptedValueStartsWithIv() {
    // given
    String input = "password";

    // when
    String encrypted = AesUtil.encrypt(input, KEY);

    // then
    String iv = encrypted.substring(0, AesUtil.IV_BASE64_LENGTH);
    assertThat(Base64.getDecoder().decode(iv)).hasSize(AesUtil.IV_LENGTH);
  }

  @Test
  @DisplayName("SECRET-005: decryption with a wrong key fails")
  void decryptWithWrongKeyFails() {
    // given
    String encrypted = AesUtil.encrypt("password", KEY);

    // when / then
    assertThatThrownBy(() -> AesUtil.decrypt(encrypted, OTHER_KEY))
        .isInstanceOf(AEADBadTagException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "AAAAAAAAAAAAAAAA"})
  void decryptRejectsTooShortInput(String input) {
    // when / then
    assertThatThrownBy(() -> AesUtil.decrypt(input, KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too short");
  }
}
