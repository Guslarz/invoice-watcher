package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.eventproducer.api.Event;
import com.guslarz.invoicewatcher.eventproducer.api.EventEnvelope;
import com.guslarz.invoicewatcher.eventproducer.api.EventProducer;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import com.guslarz.invoicewatcher.mailattachment.api.service.MailAttachmentService;
import com.guslarz.invoicewatcher.mailconnector.api.FolderMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailProcessorService {

  private final MailProducerTaskProperties properties;
  private final EventProducer eventProducer;
  private final MailAttachmentService attachments;

  public void processMail(MailProcessorContext context, long uidValidity, FolderMessage message) {
    String sourceId = MailSourceIdentity.create(context, uidValidity, message.uid());
    eventProducer.verifyTopic(properties.topic(), properties.expectedRetention().toMillis());
    AttachmentManifest manifest =
        attachments.capture(context.user().name(), sourceId, message.message().attachmentRoot());
    attachments.requirePublicationWindow(manifest, properties.expectedRetention());
    MailIngestedPayload payload = MailIngestedPayload.from(sourceId, message.message(), manifest);
    eventProducer.send(
        properties.topic(),
        Event.<MailIngestedPayload>builder()
            .key(context.user().name())
            .value(new EventEnvelope<>("mail.ingested", 1, payload))
            .build());
  }
}
