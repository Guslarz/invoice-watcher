package com.guslarz.invoicewatcher.mailconnector.impl;

import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class MimeBodyReaderTest {

  private final MimeBodyReader uut = new MimeBodyReader();

  @ParameterizedTest
  @CsvSource({
      "text/plain,base64,ISO-8859-1,Y2Fm6Q==,café",
      "text/html,quoted-printable,UTF-8,<p>caf=C3=A9</p>,<p>café</p>"
  })
  @DisplayName("MAIL-031: decodes transfer encoding and charset while preserving HTML")
  void decodesBody(String type, String encoding, String charset, String encoded, String expected) throws Exception {
    // given
    MimeMessage message = rawMessage(type + "; charset=" + charset, encoding, encoded);

    // when
    MimeBodyReader.Body result = uut.read(message);

    // then
    assertThat(result).isEqualTo(new MimeBodyReader.Body(expected, type));
  }

  @Test
  @DisplayName("MAIL-031: alternatives prefer nested related HTML and exclude related resources")
  void prefersNestedHtmlWithoutResources() throws Exception {
    // given
    MimeBodyPart root = text("<script>untrusted()</script><p>body</p>", "html");
    root.setHeader("Content-ID", "<root>");
    MimeBodyPart related = container("related", text("resource", "html"), root, text("another resource", "plain"));
    related.setHeader("Content-Type", related.getContentType() + "; start=\"<root>\"");
    MimeBodyPart alternative = container("alternative", text("fallback", "plain"), related);

    // when
    MimeBodyReader.Body result = uut.read(alternative);

    // then
    assertThat(result).isEqualTo(new MimeBodyReader.Body("<script>untrusted()</script><p>body</p>", "text/html"));
  }

  @Test
  @DisplayName("MAIL-031: related containers only use their first part when no root is specified")
  void excludesRelatedResourcesWithoutStart() throws Exception {
    // given
    MimeBodyPart unsupported = new MimeBodyPart();
    unsupported.setContent("image", "image/png");
    unsupported.setHeader("Content-Type", "image/png");
    MimeBodyPart related = container("related", unsupported, text("resource, not body", "html"));

    // when
    MimeBodyReader.Body result = uut.read(related);

    // then
    assertThat(result).isEqualTo(new MimeBodyReader.Body(null, null));
  }

  @Test
  @DisplayName("MAIL-031: mixed containers exclude attachments and attached messages, then use MIME order")
  void selectsFirstBodyWithoutAttachments() throws Exception {
    // given
    MimeBodyPart attachment = text("attachment", "html");
    attachment.setDisposition(Part.ATTACHMENT);
    MimeBodyPart named = text("named attachment", "plain");
    named.setFileName("invoice.txt");
    MimeBodyPart attachedContainer = container("alternative", text("attached HTML", "html"));
    attachedContainer.setDisposition(Part.ATTACHMENT);
    MimeBodyPart attachedMessage = new MimeBodyPart();
    attachedMessage.setContent(rawMessage("text/html", "7bit", "attached email"), "message/rfc822");
    attachedMessage.setHeader("Content-Type", "message/rfc822");
    MimeBodyPart mixed = container("mixed", attachment, named, attachedContainer, attachedMessage,
        container("mixed", text("first", "plain"), text("later HTML", "html")), text("last", "html"));

    // when
    MimeBodyReader.Body result = uut.read(mixed);

    // then
    assertThat(result).isEqualTo(new MimeBodyReader.Body("first", "text/plain"));
  }

  @Test
  @DisplayName("MAIL-031: alternatives fall back to plain text without concatenating bodies")
  void fallsBackToFirstPlainAlternative() throws Exception {
    // given
    MimeBodyPart alternative = container("alternative", container("mixed", text("first", "plain")),
        text("second", "plain"));

    // when
    MimeBodyReader.Body result = uut.read(alternative);

    // then
    assertThat(result).isEqualTo(new MimeBodyReader.Body("first", "text/plain"));
  }

  @Test
  @DisplayName("MAIL-032: distinguishes empty supported bodies from unsupported bodies")
  void distinguishesEmptyAndAbsentBodies() throws Exception {
    // given
    MimeMessage empty = rawMessage("text/plain; charset=UTF-8", "7bit", "");
    MimeMessage unsupported = rawMessage("application/pdf", "7bit", "not a body");

    // when
    MimeBodyReader.Body emptyResult = uut.read(empty);
    MimeBodyReader.Body absentResult = uut.read(unsupported);

    // then
    assertThat(emptyResult).isEqualTo(new MimeBodyReader.Body("", "text/plain"));
    assertThat(absentResult).isEqualTo(new MimeBodyReader.Body(null, null));
  }

  @Test
  @DisplayName("MAIL-032: decoding errors propagate instead of becoming absent bodies")
  void propagatesDecodingFailure() throws Exception {
    // given
    MimeMessage message = rawMessage("text/plain; charset=no-such-charset", "base64", "YWJj");

    // when
    Throwable thrown = catchThrowable(() -> uut.read(message));

    // then
    assertThat(thrown).isInstanceOf(IOException.class);
  }

  private static MimeMessage rawMessage(String type, String encoding, String body) throws Exception {
    String raw = "MIME-Version: 1.0\r\nContent-Type: " + type
        + "\r\nContent-Transfer-Encoding: " + encoding + "\r\n\r\n" + body;
    return new MimeMessage(Session.getInstance(new Properties()),
        new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
  }

  private static MimeBodyPart text(String body, String subtype) throws Exception {
    MimeBodyPart part = new MimeBodyPart();
    part.setText(body, "UTF-8", subtype);
    part.setHeader("Content-Type", "text/" + subtype + "; charset=UTF-8");
    return part;
  }

  private static MimeBodyPart container(String subtype, MimeBodyPart... children) throws Exception {
    MimeMultipart multipart = new MimeMultipart(subtype);
    for (MimeBodyPart child : children) {
      multipart.addBodyPart(child);
    }
    MimeBodyPart part = new MimeBodyPart();
    part.setContent(multipart);
    part.setHeader("Content-Type", multipart.getContentType());
    return part;
  }
}
