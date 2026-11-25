package com.guslarz.invoicewatcher.mailattachment.api.service;

import java.io.IOException;
import java.io.InputStream;

/** Lazy MIME access valid only during the connector's open-folder callback. */
public interface AttachmentPart {
  boolean attachedMessage();

  boolean candidate();

  String mediaType();

  int childCount();

  AttachmentPart child(int index);

  InputStream openStream() throws IOException;
}
