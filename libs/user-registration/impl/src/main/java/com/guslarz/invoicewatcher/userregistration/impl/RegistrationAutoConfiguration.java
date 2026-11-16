package com.guslarz.invoicewatcher.userregistration.impl;

import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthProperties;
import com.guslarz.invoicewatcher.userregistration.api.RegistrationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties({RegistrationProperties.class, GoogleOauthProperties.class})
@Import({
  RegistrationService.class,
  RegistrationController.class,
  RegistrationSecurity.class,
  RegistrationLoginHandler.class,
  RegistrationErrors.class,
  RequestSizeFilter.class
})
public class RegistrationAutoConfiguration {}
