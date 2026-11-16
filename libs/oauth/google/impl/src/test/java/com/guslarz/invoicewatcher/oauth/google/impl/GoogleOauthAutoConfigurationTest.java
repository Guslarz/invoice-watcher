package com.guslarz.invoicewatcher.oauth.google.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.GmailGrantDao;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthProperties;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthService;
import com.guslarz.invoicewatcher.secret.aes.SecretAesAutoConfiguration;
import com.guslarz.invoicewatcher.secret.aes.SecretAesProperties;
import com.guslarz.invoicewatcher.secret.aes.SecretFactoryAesImpl;
import java.util.Base64;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class GoogleOauthAutoConfigurationTest {

  private final String key = Base64.getEncoder().encodeToString(new byte[32]);
  private final String encryptedSecret =
      new SecretFactoryAesImpl(
              new SecretAesProperties(new SecretKeySpec(new byte[32], "AES")))
          .fromPlaintext("fixture-client-secret")
          .getEncrypted();
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GoogleOauthAutoConfiguration.class, SecretAesAutoConfiguration.class))
          .withBean(GmailGrantDao.class, () -> mock(GmailGrantDao.class))
          .withPropertyValues("invoice-watcher.secret.aes.key=" + key);

  @Test
  @DisplayName("REG-016: discovers the relocated Google auto-configuration")
  void registersRelocatedAutoConfiguration() {
    // given
    ClassLoader classLoader = getClass().getClassLoader();

    // when
    var candidates = ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates();

    // then
    assertThat(candidates).contains(GoogleOauthAutoConfiguration.class.getName());
  }

  @Test
  @DisplayName("REG-016: binds provider-scoped settings and decrypts the client secret")
  void bindsGoogleSettings() {
    // given
    var runner =
        uut.withPropertyValues(
            "invoice-watcher.oauth.google.client-id=fixture-client",
            "invoice-watcher.oauth.google.client-secret=" + encryptedSecret);

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(GoogleOauthService.class);
          var properties = context.getBean(GoogleOauthProperties.class);
          assertThat(properties.clientId()).isEqualTo("fixture-client");
          assertThat(properties.clientSecret().getEncrypted()).isEqualTo(encryptedSecret);
          assertThat(properties.clientSecret().getDecrypted()).isEqualTo("fixture-client-secret");
        });
  }

  @Test
  @DisplayName("REG-016: workload environment variables bind the Google client")
  void bindsWorkloadEnvironmentVariables() {
    // given
    var runner =
        uut.withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource(
                            "workload",
                            Map.of(
                                "INVOICE_WATCHER_OAUTH_GOOGLE_CLIENT_ID", "fixture-client",
                                "INVOICE_WATCHER_OAUTH_GOOGLE_CLIENT_SECRET", encryptedSecret))));

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(GoogleOauthService.class);
          var properties = context.getBean(GoogleOauthProperties.class);
          assertThat(properties.clientId()).isEqualTo("fixture-client");
          assertThat(properties.clientSecret().getDecrypted()).isEqualTo("fixture-client-secret");
        });
  }

  @Test
  @DisplayName("REG-016: the old prefix does not activate Google")
  void ignoresFormerPrefix() {
    // given
    var runner =
        uut.withPropertyValues(
            "invoice-watcher.google-oauth.client-id=fixture-client",
            "invoice-watcher.google-oauth.client-secret=" + encryptedSecret);

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().doesNotHaveBean(GoogleOauthService.class);
          assertThat(context).doesNotHaveBean(GoogleOauthProperties.class);
        });
  }

  @Test
  @DisplayName("REG-016: requires a client secret when Google is configured")
  void rejectsMissingSecret() {
    // given
    var runner = uut.withPropertyValues("invoice-watcher.oauth.google.client-id=fixture-client");

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(BindValidationException.class)
              .hasStackTraceContaining("clientSecret");
        });
  }

  @Test
  @DisplayName("REG-016: rejects a blank Google client identifier")
  void rejectsBlankClientId() {
    // given
    var runner =
        uut.withPropertyValues(
            "invoice-watcher.oauth.google.client-id= ",
            "invoice-watcher.oauth.google.client-secret=" + encryptedSecret);

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(BindValidationException.class)
              .hasStackTraceContaining("clientId");
        });
  }

  @Test
  @DisplayName("REG-016: independently configured provider beans coexist")
  void coexistsWithAnotherProvider() {
    // given
    var runner =
        uut.withUserConfiguration(ExampleProviderConfiguration.class)
            .withPropertyValues(
                "invoice-watcher.oauth.example.client-id=example-client",
                "invoice-watcher.oauth.google.client-id=google-client",
                "invoice-watcher.oauth.google.client-secret=" + encryptedSecret);

    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(GoogleOauthService.class);
          assertThat(context.getBean(GoogleOauthProperties.class).clientId())
              .isEqualTo("google-client");
          assertThat(context.getBean("exampleOauthService")).isEqualTo("example-client");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ExampleProviderProperties.class)
  static class ExampleProviderConfiguration {

    @Bean
    String exampleOauthService(ExampleProviderProperties properties) {
      return properties.clientId();
    }
  }

  @ConfigurationProperties("invoice-watcher.oauth.example")
  record ExampleProviderProperties(String clientId) {}
}
