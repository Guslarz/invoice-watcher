package com.guslarz.invoicewatcher.oauth.google.api;

import com.guslarz.invoicewatcher.secret.api.Secret;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Google client shared by login, Gmail authorization and background token refresh.
 *
 * @param clientId Required. Google web OAuth client identifier.
 * @param clientSecret Required. Google web OAuth client secret, provided outside version control.
 */
@Validated
@ConfigurationProperties("invoice-watcher.oauth.google")
public record GoogleOauthProperties(@NotBlank String clientId, @NotNull Secret clientSecret) {}
