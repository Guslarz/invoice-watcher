package com.guslarz.invoicewatcher.googleoauth.impl;

import com.guslarz.invoicewatcher.googleoauth.api.GoogleOauthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(prefix = "invoice-watcher.google-oauth", name = "client-id")
@AutoConfiguration
@EnableConfigurationProperties(GoogleOauthProperties.class)
@Import(GoogleOauthServiceImpl.class)
public class GoogleOauthAutoConfiguration {}
