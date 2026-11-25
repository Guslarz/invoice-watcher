package com.guslarz.invoicewatcher.mailconnector.impl.service;

import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import com.guslarz.invoicewatcher.mailattachment.api.service.AttachmentPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class JakartaAttachmentPart implements AttachmentPart {
  private final Part part;

  @Override
  public boolean attachedMessage() {
    try {
      return part.isMimeType("message/*");
    } catch (MessagingException exception) {
      throw failure();
    }
  }

  @Override
  public boolean candidate() {
    try {
      return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
    } catch (MessagingException exception) {
      throw failure();
    }
  }

  @Override
  public String mediaType() {
    try {
      return new ContentType(part.getContentType()).getBaseType().toLowerCase(Locale.ROOT);
    } catch (MessagingException exception) {
      throw failure();
    }
  }

  @Override
  public int childCount() {
    try {
      return part.isMimeType("multipart/*") ? multipart().getCount() : 0;
    } catch (MessagingException exception) {
      throw failure();
    }
  }

  @Override
  public AttachmentPart child(int index) {
    try {
      return new JakartaAttachmentPart(multipart().getBodyPart(index));
    } catch (MessagingException exception) {
      throw failure();
    }
  }

  @Override
  public InputStream openStream() throws IOException {
    try {
      return part.getInputStream();
    } catch (MessagingException exception) {
      throw failure();
    }
  }

  private Multipart multipart() {
    try {
      Object content = part.getContent();
      if (content instanceof Multipart multipart) {
        return multipart;
      }
      throw failure();
    } catch (MessagingException | IOException exception) {
      throw failure();
    }
  }

  private static AttachmentException failure() {
    return new AttachmentException("mime-decoding-failure");
  }
}
