package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailProducerScheduler {

  private final MailProducerSchedulerProperties properties;
  private final TaskScheduler taskScheduler;
  private final MailProducerTask task;

  @PostConstruct
  public void schedule() {
    taskScheduler.scheduleAtFixedRate(task::execute, properties.rate());
  }
}
