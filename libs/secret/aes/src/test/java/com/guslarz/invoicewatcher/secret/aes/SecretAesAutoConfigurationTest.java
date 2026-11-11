package com.guslarz.invoicewatcher.secret.aes;

import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import com.guslarz.invoicewatcher.util.aes.AesUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class SecretAesAutoConfigurationTest {

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SecretAesAutoConfiguration.class));

  private static List<ConfigurationPropertyName> invalidProperties(Throwable startupFailure) {
    BindValidationException exception =
        (BindValidationException) NestedExceptionUtils.getRootCause(startupFailure);
    String prefix = Objects.requireNonNull(exception).getValidationErrors().getName().toString();
    return exception.getValidationErrors().getAllErrors().stream()
        .map(error -> ConfigurationPropertyName.adapt(
            prefix + "." + ((FieldError) error).getField(), '.'))
        .toList();
  }

  @Test
  @DisplayName("SECRET-003: fails to start without the key")
  void failsWithoutKey() {
    // given
    ApplicationContextRunner runner = uut;

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure())).containsExactly(
          ConfigurationPropertyName.of("invoice-watcher.secret.aes.key"));
    });
  }

  @Test
  @DisplayName("SECRET-003: starts with the key")
  void startsWithKey() {
    // given
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    ApplicationContextRunner runner = uut
        .withPropertyValues("invoice-watcher.secret.aes.key=" + key);

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(SecretFactory.class);
    });
  }

  @Test
  @DisplayName("SECRET-003: binds the Base64 key used to decrypt stored secrets")
  void bindsKeyForSecretFactory() {
    // given
    SecretKey key = AesUtil.decodeSecretKey("4yWK+SIr9BkkB3ymrJ1A1mtQLH7opt27zItE7akvJ9w=");
    String encrypted = AesUtil.encrypt("password", key);
    ApplicationContextRunner runner = uut.withPropertyValues(
        "invoice-watcher.secret.aes.key=" + Base64.getEncoder().encodeToString(key.getEncoded()));

    // when
    runner.run(context -> {
      Secret secret =
          context.getBean(SecretFactory.class).fromEncrypted(encrypted);

      // then
      assertThat(context).hasNotFailed();
      assertThat(secret.getEncrypted()).isEqualTo(encrypted);
      assertThat(secret.getDecrypted()).isEqualTo("password");
    });
  }

  @Test
  @DisplayName("SECRET-004: wrapping a stored value does not decrypt it")
  void defersDecryptionOfStoredValue() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        "invoice-watcher.secret.aes.key=" + Base64.getEncoder().encodeToString(new byte[32]));

    // when
    runner.run(context -> {
      Secret secret =
          context.getBean(SecretFactory.class).fromEncrypted("not-yet-decryptable");

      // then
      assertThat(secret.getEncrypted()).isEqualTo("not-yet-decryptable");
    });
  }
}
