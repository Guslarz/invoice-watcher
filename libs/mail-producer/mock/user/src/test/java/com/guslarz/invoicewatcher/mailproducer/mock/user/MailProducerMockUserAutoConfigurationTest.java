package com.guslarz.invoicewatcher.mailproducer.mock.user;

import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MailProducerMockUserAutoConfigurationTest {

  private static final String Prefix = "invoice-watcher.mail-producer.mock.user.";

  private final ApplicationContextRunner uut = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MailProducerMockUserAutoConfiguration.class))
      .withBean(UserDaoService.class, () -> mock(UserDaoService.class));

  private static List<ConfigurationPropertyName> invalidProperties(Throwable startupFailure) {
    BindValidationException exception =
        (BindValidationException) NestedExceptionUtils.getRootCause(startupFailure);
    String prefix = Objects.requireNonNull(exception).getValidationErrors().getName().toString();
    return exception.getValidationErrors().getAllErrors().stream()
        .map(error -> ConfigurationPropertyName.adapt(
            prefix + "." + ((FieldError) error).getField(), '.'))
        .toList();
  }

  @Test
  @DisplayName("USER-030: fails to start when the mock user is enabled without host")
  void failsWhenEnabledWithoutHost() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Prefix + "enabled=true",
        Prefix + "email=jan@example.com",
        Prefix + "folders=INBOX"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure()))
          .contains(ConfigurationPropertyName.of(Prefix + "host"))
          .doesNotContain(ConfigurationPropertyName.of(Prefix + "email"));
    });
  }

  @Test
  @DisplayName("USER-030: fails to start when the mock user port is out of range")
  void failsWhenPortIsOutOfRange() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(
        Prefix + "enabled=true",
        Prefix + "port=0"
    );

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasFailed();
      assertThat(invalidProperties(context.getStartupFailure()))
          .contains(ConfigurationPropertyName.of(Prefix + "port"));
    });
  }

  @Test
  @DisplayName("USER-030: starts without other mock user properties when the mock user is disabled")
  void startsWhenDisabled() {
    // given
    ApplicationContextRunner runner = uut.withPropertyValues(Prefix + "enabled=false");

    // when
    runner.run(context -> {

      // then
      assertThat(context).hasNotFailed();
      assertThat(context).doesNotHaveBean(MailProducerMockUserInitializer.class);
    });
  }
}
