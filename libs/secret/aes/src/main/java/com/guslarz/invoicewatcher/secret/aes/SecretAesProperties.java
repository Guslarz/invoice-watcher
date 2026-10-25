package com.guslarz.invoicewatcher.secret.aes;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;

/**
 * AES encryption of stored secrets.
 *
 * @param key Required. Base64-encoded AES-256 key, as printed by `secret-helper generate-key`.
 */
@Validated
@ConfigurationProperties("invoice-watcher.secret.aes")
public record SecretAesProperties(
    @NotNull SecretKey key
) {
}
