package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

import com.guslarz.invoicewatcher.secret.api.Secret;
import lombok.Builder;

@Builder
public record BasicAuthMailCredentials(
    String username,
    Secret password
) implements MailCredentials {

  @Override
  public <T> T accept(MailCredentialsVisitor<T> converter) {
    return converter.visitBasicMailCredentials(this);
  }
}
