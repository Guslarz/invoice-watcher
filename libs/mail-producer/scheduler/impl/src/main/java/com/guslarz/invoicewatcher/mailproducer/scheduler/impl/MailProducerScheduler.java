package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailProducerScheduler {

  private final MailProducerSchedulerProperties properties;
  private final TaskScheduler taskScheduler;
  private final MailProducerTask task;

  @EventListener(ApplicationReadyEvent.class)
  public void schedule() {
    taskScheduler.scheduleAtFixedRate(task::execute, properties.rate());
  }
}
