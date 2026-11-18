package com.guslarz.invoicewatcher.eventprocessor.api.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/** Validation and byte encoding shared by processor contract models. */
@UtilityClass
public class ProcessingModelSupport {
  public static final Pattern EVENT_TYPE = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+");
  public static final Pattern TOPIC = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,248}");
  public static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

  public static void identifier(String value) {
    if (value == null || !IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid operational identifier");
    }
  }

  public static String encode(byte[] bytes) {
    return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
  }

  public static String encodeText(String text) {
    return encode(text.getBytes(StandardCharsets.UTF_8));
  }

  public static byte[] decode(String value) {
    return value == null ? null : Base64.getDecoder().decode(value);
  }
}
