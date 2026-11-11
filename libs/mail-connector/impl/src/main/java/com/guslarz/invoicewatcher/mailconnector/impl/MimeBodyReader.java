package com.guslarz.invoicewatcher.mailconnector.impl;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;

import java.io.IOException;

/** Selects decoded bodies without including attachments or related resources. */
final class MimeBodyReader {

  private static final Body ABSENT = new Body(null, null);

  Body read(Part part) throws MessagingException, IOException {
    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null
        || part.isMimeType("message/*")) {
      return ABSENT;
    }
    if (part.isMimeType("text/html") || part.isMimeType("text/plain")) {
      String type = part.isMimeType("text/html") ? "text/html" : "text/plain";
      // Jakarta Mail decodes the transfer encoding and declared charset.
      Object content = part.getContent();
      if (!(content instanceof String text)) {
        throw new MessagingException("Supported body did not decode to text");
      }
      return new Body(text, type);
    }
    if (!part.isMimeType("multipart/*")) {
      return ABSENT;
    }
    Object content = part.getContent();
    if (!(content instanceof Multipart multipart)) {
      throw new MessagingException("Multipart body did not decode to a multipart container");
    }
    if (part.isMimeType("multipart/related")) {
      return readRelated(part, multipart);
    }
    boolean alternative = part.isMimeType("multipart/alternative");
    Body selected = ABSENT;
    for (int i = 0; i < multipart.getCount(); i++) {
      Body candidate = read(multipart.getBodyPart(i));
      if (candidate.contentType() == null) {
        continue;
      }
      if (!alternative || "text/html".equals(candidate.contentType())) {
        return candidate;
      }
      if (selected.contentType() == null) {
        selected = candidate;
      }
    }
    return selected;
  }

  private Body readRelated(Part part, Multipart multipart) throws MessagingException, IOException {
    String start = new ContentType(part.getContentType()).getParameter("start");
    // The root is identified by start, or is the first part when start is absent.
    for (int i = 0; i < multipart.getCount(); i++) {
      BodyPart child = multipart.getBodyPart(i);
      if (start == null) {
        return read(child);
      }
      String[] ids = child.getHeader("Content-ID");
      if (ids != null) {
        for (String id : ids) {
          if (start.equals(id)) {
            return read(child);
          }
        }
      }
    }
    return ABSENT;
  }

  record Body(String content, String contentType) {
  }
}
