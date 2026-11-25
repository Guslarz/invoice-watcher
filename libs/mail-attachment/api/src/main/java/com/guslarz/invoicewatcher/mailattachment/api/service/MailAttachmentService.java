package com.guslarz.invoicewatcher.mailattachment.api.service;

import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentDescriptor;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import java.time.Duration;

public interface MailAttachmentService {
  AttachmentManifest capture(String user, String sourceId, AttachmentPart root);

  void requirePublicationWindow(AttachmentManifest manifest, Duration inputRetention);

  byte[] read(String user, String sourceId, AttachmentDescriptor attachment);
}
