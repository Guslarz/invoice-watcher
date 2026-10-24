package com.guslarz.invoicewatcher.secret.helper.app;

import com.guslarz.invoicewatcher.util.aes.AesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecretHelperRunnerTest {

  private static final String KEY = "4yWK+SIr9BkkB3ymrJ1A1mtQLH7opt27zItE7akvJ9w=";
  private static final String OTHER_KEY = "Qk9Hk2m1xv8n0pYt3cR7sLw5aZ9eJ4uF6hD2gB8iK0M=";
  private static final String PLAINTEXT = "mailbox-password";

  private static final InputStream ORIGINAL_IN = System.in;
  private static final PrintStream ORIGINAL_OUT = System.out;
  private static final PrintStream ORIGINAL_ERR = System.err;

  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @AfterEach
  void restoreStreams() {
    System.setIn(ORIGINAL_IN);
    System.setOut(ORIGINAL_OUT);
    System.setErr(ORIGINAL_ERR);
  }

  @Test
  @DisplayName("SECRET-010: generate-key prints a different random 256-bit key every time")
  void generateKeyPrintsRandomKey() {
    // when
    Result first = run("", "generate-key");
    Result second = run("", "generate-key");

    // then
    assertThat(first.status()).isZero();
    assertThat(second.status()).isZero();
    assertThat(first.out()).isNotEqualTo(second.out());
    assertThat(Base64.getDecoder().decode(first.out())).hasSize(32);
    assertThat(Base64.getDecoder().decode(second.out())).hasSize(32);
    assertThat(run(first.out() + "\n" + PLAINTEXT + "\n", "encrypt").status()).isZero();
    assertThat(run(second.out() + "\n" + PLAINTEXT + "\n", "encrypt").status()).isZero();
  }

  @Test
  @DisplayName("SECRET-012: decrypt prints the value encrypted with a generated key")
  void decryptPrintsOriginalValue() {
    // given
    String key = run("", "generate-key").out();
    String encrypted = printedValue(run(key + "\n" + PLAINTEXT + "\n", "encrypt").out());

    // when
    Result result = run(key + "\n" + encrypted + "\n", "decrypt");

    // then
    assertThat(result.status()).isZero();
    assertThat(printedValue(result.out())).isEqualTo(PLAINTEXT);
  }

  @Test
  @DisplayName("SECRET-014: without a command the usage is printed on standard error")
  void noCommandPrintsUsage() {
    // when
    Result result = run("");

    // then
    assertThat(result.status()).isEqualTo(2);
    assertThat(result.err()).contains("Usage: secret-helper");
    assertThat(result.out()).isEmpty();
  }

  @Test
  @DisplayName("SECRET-014: an unknown option prints the usage on standard error")
  void unknownOptionPrintsUsage() {
    // when
    Result result = run("", "generate-key", "--mode", "password");

    // then
    assertThat(result.status()).isEqualTo(2);
    assertThat(result.err()).contains("Usage: secret-helper generate-key");
    assertThat(result.out()).isEmpty();
  }

  @Test
  @DisplayName("SECRET-015: decrypt with a wrong key prints one line and no plaintext")
  void decryptWithWrongKeyFailsBriefly() {
    // given
    String encrypted = AesUtil.encrypt(PLAINTEXT, AesUtil.decodeSecretKey(KEY));

    // when
    Result result = run(OTHER_KEY + "\n" + encrypted + "\n", "decrypt");

    // then
    assertThat(result.status()).isEqualTo(1);
    assertThat(result.err().lines()).hasSize(1);
    assertThat(result.out()).doesNotContain(PLAINTEXT);
    assertThat(result.err()).doesNotContain(PLAINTEXT);
  }

  @Test
  @DisplayName("SECRET-015: encrypt with empty standard input prints one line")
  void encryptWithoutInputFailsBriefly() {
    // when
    Result result = run("", "encrypt");

    // then
    assertThat(result.status()).isEqualTo(1);
    assertThat(result.err().lines()).hasSize(1);
  }

  @Test
  @DisplayName("SECRET-015: encrypt with an invalid key prints one line")
  void encryptWithInvalidKeyFailsBriefly() {
    // when
    Result result = run("c2hvcnQ=\n" + PLAINTEXT + "\n", "encrypt");

    // then
    assertThat(result.status()).isEqualTo(1);
    assertThat(result.err().lines()).hasSize(1);
    assertThat(result.err()).doesNotContain(PLAINTEXT);
  }

  @Test
  void springArgumentsAreIgnored() {
    // when
    Result result = run("", "--spring.main.banner-mode=off", "generate-key");

    // then
    assertThat(result.status()).isZero();
  }

  private Result run(String input, String... args) {
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    int status = SecretHelperRunner.execute(args);
    System.out.flush();
    System.err.flush();
    return new Result(status, out.toString(StandardCharsets.UTF_8).strip(),
        err.toString(StandardCharsets.UTF_8).strip());
  }

  /** The prompts end without a line break, so the result follows the last one. */
  private static String printedValue(String out) {
    return out.substring(out.lastIndexOf(": ") + 2);
  }

  private record Result(int status, String out, String err) {
  }
}
