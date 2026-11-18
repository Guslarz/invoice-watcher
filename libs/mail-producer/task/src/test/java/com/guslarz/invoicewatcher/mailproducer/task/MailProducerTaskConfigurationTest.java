package com.guslarz.invoicewatcher.mailproducer.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.FolderConfigDaoService;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.service.UserDaoService;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionFactory;
import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class MailProducerTaskConfigurationTest {
  @Test
  @DisplayName("EVENT-010: active scanning rejects missing or unsupported publishing backends")
  void rejectsMissingPublisher() {
    // given
    var runner = activeScanner().withBean(UserDaoService.class, () -> mock(UserDaoService.class));
    // when
    for (String mode : List.of("", "unknown")) {
      runner
          .withPropertyValues("invoice-watcher.event-producer.mode=" + mode)
          .run(
              context -> {
                // then
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(EventProducer.class.getName());
              });
    }
  }

  @Test
  @DisplayName("USER-010: active scanning requires storage instead of silently disabling itself")
  void rejectsMissingStorage() {
    // given
    var runner = activeScanner().withBean(EventProducer.class, () -> mock(EventProducer.class));
    // when
    for (String mode : List.of("", "unknown")) {
      runner
          .withPropertyValues("invoice-watcher.user-configuration.dao.mode=" + mode)
          .run(
              context -> {
                // then
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining(UserDaoService.class.getName());
              });
    }
  }

  @Test
  @DisplayName("EVENT-010: active scanning rejects ambiguous event producer implementations")
  void rejectsAmbiguousPublishers() {
    // given
    var runner =
        activeScanner()
            .withBean(UserDaoService.class, () -> mock(UserDaoService.class))
            .withBean("firstPublisher", EventProducer.class, () -> mock(EventProducer.class))
            .withBean("secondPublisher", EventProducer.class, () -> mock(EventProducer.class));
    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining("NoUniqueBeanDefinitionException")
              .hasStackTraceContaining(EventProducer.class.getName());
        });
  }

  @Test
  @DisplayName("EVENT-010: a future backend composes without changing shared APIs")
  void acceptsImplementationContributedSelection() {
    // given
    var runner =
        activeScanner()
            .withConfiguration(AutoConfigurations.of(FutureBackend.class))
            .withBean(UserDaoService.class, () -> mock(UserDaoService.class))
            .withPropertyValues("invoice-watcher.event-producer.mode=future");
    // when
    runner.run(
        context -> {
          // then
          assertThat(context)
              .hasNotFailed()
              .hasSingleBean(EventProducer.class)
              .hasSingleBean(MailProducerTask.class);
        });
  }

  private ApplicationContextRunner activeScanner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailProducerTaskAutoConfiguration.class))
        .withBean(FolderConfigDaoService.class, () -> mock(FolderConfigDaoService.class))
        .withBean(MailConnectionFactory.class, () -> mock(MailConnectionFactory.class))
        .withPropertyValues("invoice-watcher.mail-producer.task.topic=fixture");
  }

  @AutoConfiguration
  @ConditionalOnProperty(
      prefix = "invoice-watcher.event-producer",
      name = "mode",
      havingValue = "future")
  static class FutureBackend {
    @Bean
    EventProducer futureProducer() {
      return mock(EventProducer.class);
    }
  }
}
