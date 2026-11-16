package com.guslarz.invoicewatcher.oauth.google.impl;

import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(prefix = "invoice-watcher.oauth.google", name = "client-id")
@AutoConfiguration
@EnableConfigurationProperties(GoogleOauthProperties.class)
@Import(GoogleOauthServiceImpl.class)
public class GoogleOauthAutoConfiguration {}
