package com.guslarz.invoicewatcher.userregistration.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.RegistrationDao;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegistrationServiceWiringTest {
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner()
          .withUserConfiguration(RegistrationService.class)
          .withBean(GoogleOauthService.class, () -> mock(GoogleOauthService.class));

  @Test
  @DisplayName("REG-015 USER-010: registration requires its storage interface")
  void rejectsMissingStorage() {
    // given
    // when
    uut.run(
        context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining(RegistrationDao.class.getName());
        });
  }

  @Test
  @DisplayName("REG-015 USER-010: registration rejects ambiguous required storage implementations")
  void rejectsAmbiguousStorage() {
    // given
    var runner =
        uut.withBean("firstStore", RegistrationDao.class, () -> mock(RegistrationDao.class))
            .withBean("secondStore", RegistrationDao.class, () -> mock(RegistrationDao.class));
    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining("NoUniqueBeanDefinitionException")
              .hasStackTraceContaining(RegistrationDao.class.getName());
        });
  }

  @Test
  @DisplayName("REG-015 USER-010: registration composes with its required storage contract")
  void composesRequiredStorage() {
    // given
    var runner = uut.withBean(RegistrationDao.class, () -> mock(RegistrationDao.class));
    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(RegistrationService.class);
        });
  }
}
