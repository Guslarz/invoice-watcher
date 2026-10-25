package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class MailProducerSchedulerTest {

  private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
  private final ApplicationRunner mockUserInitializer = mock(ApplicationRunner.class);

  private final SpringApplicationBuilder uut = new SpringApplicationBuilder(
      MailProducerSchedulerAutoConfiguration.class)
      .web(WebApplicationType.NONE)
      .initializers(context -> {
        context.getBeanFactory().registerSingleton("taskScheduler", taskScheduler);
        context.getBeanFactory().registerSingleton("task", mock(MailProducerTask.class));
        context.getBeanFactory().registerSingleton("mockUserInitializer", mockUserInitializer);
      });

  @Test
  @DisplayName("MAIL-001, USER-031: schedules the first scan only after application runners "
      + "such as the mock user initializer have finished")
  void schedulesAfterApplicationRunners() throws Exception {
    // given
    InOrder startup = inOrder(mockUserInitializer, taskScheduler);

    // when
    try (ConfigurableApplicationContext context =
             uut.run("--invoice-watcher.mail-producer.scheduler.rate=10s")) {

      // then
      startup.verify(mockUserInitializer).run(any());
      startup.verify(taskScheduler).scheduleAtFixedRate(any(), eq(Duration.ofSeconds(10)));
    }
  }
}
