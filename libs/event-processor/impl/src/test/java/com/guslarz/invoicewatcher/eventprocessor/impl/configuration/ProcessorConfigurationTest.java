package com.guslarz.invoicewatcher.eventprocessor.impl.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import com.guslarz.invoicewatcher.dao.eventprocessing.firestore.configuration.ProcessingFirestoreAutoConfiguration;
import com.guslarz.invoicewatcher.eventprocessor.api.model.EventContract;
import com.guslarz.invoicewatcher.eventprocessor.api.model.ProcessingStage;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorRuntime;
import com.guslarz.invoicewatcher.eventprocessor.api.service.ProcessorTransport;
import com.guslarz.invoicewatcher.eventprocessor.kafka.configuration.ProcessorKafkaAutoConfiguration;
import com.guslarz.invoicewatcher.eventprocessor.kafka.configuration.ProcessorKafkaLifecycleAutoConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProcessorConfigurationTest {
  private final ApplicationContextRunner uut =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ProcessorAutoConfiguration.class,
                  ProcessingFirestoreAutoConfiguration.class,
                  ProcessorKafkaAutoConfiguration.class,
                  ProcessorKafkaLifecycleAutoConfiguration.class));

  @Test
  @DisplayName("PROCESSOR-001: no registered stage starts no Kafka consumer or broker connection")
  void remainsInactiveWithoutStage() {
    // given
    // when
    uut.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().doesNotHaveBean(ProcessorRuntime.class);
          assertThat(context.containsBean("processorTransport")).isFalse();
          assertThat(context.containsBean("processorClock")).isFalse();
          assertThat(context.containsBean("processorExecutor")).isFalse();
          assertThat(context.containsBean("processorMeterRegistry")).isFalse();
        });
  }

  @Test
  @DisplayName("PROCESSOR-001: declared stages require explicit brokers and offset policy")
  void requiresKafkaConfigurationWhenStageDeclared() {
    // given
    var stage =
        ProcessingStage.builder()
            .name("fixture")
            .processingVersion("v1")
            .inputTopic("input")
            .resultTopic("result")
            .quarantineTopic("quarantine")
            .replayTopic("replay")
            .inputs(Set.of(new EventContract("mail.ingested", 1)))
            .outputs(Set.of(new EventContract("fixture.result", 1)))
            .handler(input -> List.of())
            .build();
    // when
    uut.withBean(ProcessingStage.class, () -> stage)
        .run(
            context -> {
              // then
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining("bootstrapServers")
                  .hasStackTraceContaining("offsetReset");
            });
  }

  @Test
  @DisplayName("PROCESSOR-001: an active stage requires storage and transport through injection")
  void rejectsMissingBackend() {
    // given
    var runner = activeCore();
    // when
    runner
        .withBean(ProcessorTransport.class, () -> mock(ProcessorTransport.class))
        .run(
            context -> {
              // then
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining(ProcessingDao.class.getName());
            });
    runner
        .withBean(ProcessingDao.class, () -> mock(ProcessingDao.class))
        .run(
            context -> {
              // then
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasStackTraceContaining(ProcessorTransport.class.getName());
            });
  }

  @Test
  @DisplayName("PROCESSOR-001: an active stage rejects ambiguous storage implementations")
  void rejectsAmbiguousStorage() {
    // given
    var runner =
        activeCore()
            .withBean(ProcessorTransport.class, () -> mock(ProcessorTransport.class))
            .withBean("firstStore", ProcessingDao.class, () -> mock(ProcessingDao.class))
            .withBean("secondStore", ProcessingDao.class, () -> mock(ProcessingDao.class));
    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasStackTraceContaining("NoUniqueBeanDefinitionException")
              .hasStackTraceContaining(ProcessingDao.class.getName());
        });
  }

  @Test
  @DisplayName("PROCESSOR-001: an active stage composes with required backend contracts")
  void composesRequiredBackends() {
    // given
    var runner =
        activeCore()
            .withBean(ProcessingDao.class, () -> mock(ProcessingDao.class))
            .withBean(ProcessorTransport.class, () -> mock(ProcessorTransport.class));
    // when
    runner.run(
        context -> {
          // then
          assertThat(context).hasNotFailed().hasSingleBean(ProcessorRuntime.class);
        });
  }

  private ApplicationContextRunner activeCore() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ProcessorAutoConfiguration.class))
        .withBean(
            ProcessingStage.class,
            () ->
                ProcessingStage.builder()
                    .name("fixture")
                    .processingVersion("v1")
                    .inputTopic("input")
                    .resultTopic("result")
                    .quarantineTopic("quarantine")
                    .replayTopic("replay")
                    .inputs(Set.of(new EventContract("mail.ingested", 1)))
                    .outputs(Set.of(new EventContract("fixture.result", 1)))
                    .handler(input -> List.of())
                    .build());
  }
}
