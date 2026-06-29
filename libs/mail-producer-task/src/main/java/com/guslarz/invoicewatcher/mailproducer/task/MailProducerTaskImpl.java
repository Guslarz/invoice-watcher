package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailproducer.scheduler.api.MailProducerTask;import lombok.extern.slf4j.Slf4j;import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MailProducerTaskImpl implements MailProducerTask {

  @Override
  public void execute() {
    log.info("DUPA");
  }
}
