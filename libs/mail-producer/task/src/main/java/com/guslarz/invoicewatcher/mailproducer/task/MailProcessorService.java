package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.mailconnector.api.MailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailProcessorService {

  private final MailProducerTaskProperties properties;
  private final EventProducer eventProducer;

  public void processMail(MailProcessorContext context, MailMessage mailMessage) {
    eventProducer.send(properties.topic(), Event.builder()
            .key(context.user().name())
            .value(mailMessage)
        .build());
  }
}
