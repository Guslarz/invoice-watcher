package com.guslarz.invoicewatcher.secret.aes;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;

// Only used to generate metadata file
/**
 * AES encryption of stored secrets.
 *
 * @param key Base64-encoded AES-256 key, as printed by `secret-helper generate-key`; required.
 */
@ConfigurationProperties("invoice-watcher.secret.aes")
public record SecretAesProperties(
    SecretKey key
) {
}
