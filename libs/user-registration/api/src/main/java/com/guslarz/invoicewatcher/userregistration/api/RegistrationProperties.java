package com.guslarz.invoicewatcher.userregistration.api;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Public registration origin and cookie transport policy.
 *
 * @param publicBaseUrl Required. Public origin, HTTPS in deployments, HTTP permitted for local
 *     development.
 * @param secureCookies Optional. Require HTTPS for session cookies; disable only for local HTTP
 *     development.
 */
@Validated
@ConfigurationProperties("invoice-watcher.user-registration")
public record RegistrationProperties(
    @NotNull URI publicBaseUrl, @DefaultValue("true") boolean secureCookies) {}
