package com.guslarz.invoicewatcher.userregistration.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthProperties;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthService;
import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import com.guslarz.invoicewatcher.userregistration.api.RegistrationProperties;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class RegistrationSecurityTest {
  @Configuration(proxyBeanMethods = false)
  @EnableWebSecurity
  @EnableWebMvc
  @Import({
    RegistrationSecurity.class,
    RegistrationLoginHandler.class,
    RegistrationService.class,
    RegistrationController.class,
    RegistrationErrors.class
  })
  static class TestConfiguration {}

  private final WebApplicationContextRunner uut =
      new WebApplicationContextRunner()
          .withUserConfiguration(TestConfiguration.class)
          .withBean(RegistrationDao.class, () -> mock(RegistrationDao.class))
          .withBean(GoogleOauthService.class, () -> mock(GoogleOauthService.class))
          .withBean(SecretFactory.class, () -> mock(SecretFactory.class))
          .withBean(
              GoogleOauthProperties.class,
              () -> {
                var secret = mock(Secret.class);
                when(secret.getDecrypted()).thenReturn("fixture");
                return new GoogleOauthProperties("fixture", secret);
              })
          .withBean(
              RegistrationProperties.class,
              () -> new RegistrationProperties(URI.create("https://register.test"), true))
          .withBean(
              OAuth2AuthorizedClientService.class,
              () ->
                  new InMemoryOAuth2AuthorizedClientService(
                      mock(ClientRegistrationRepository.class)));

  @Test
  @DisplayName("REG-008: APIs require authentication and state changes require CSRF")
  void protectsRequestsAtSecurityBoundary() {
    // given
    uut.run(
        context -> {
          var mvc =
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                  .apply(springSecurity())
                  .build();
          // when / then
          mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
          mvc.perform(post("/api/v1/me/gmail/connect").with(oidcLogin()))
              .andExpect(status().isForbidden());
          mvc.perform(get("/api/csrf")).andExpect(status().isOk());
        });
  }

  @Test
  @DisplayName("REG-003 REG-004: registration API rejects password and custom-server payloads")
  void rejectsUnsupportedCredentialFields() {
    // given
    uut.run(
        context -> {
          var mvc =
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                  .apply(springSecurity())
                  .build();
          // when / then
          mvc.perform(
                  put("/api/v1/me/configuration")
                      .with(oidcLogin())
                      .with(csrf())
                      .contentType("application/json")
                      .content("{\"version\":0,\"password\":\"secret\",\"folders\":[]}"))
              .andExpect(status().isBadRequest());
          verifyNoInteractions(context.getBean(RegistrationDao.class));
        });
  }
}
