package com.guslarz.invoicewatcher.mailattachment.api.model;

import java.util.List;

public record AttachmentManifest(
    int version, boolean complete, String limitReason, List<AttachmentDescriptor> attachments) {
  public AttachmentManifest {
    attachments = List.copyOf(attachments);
  }
}
