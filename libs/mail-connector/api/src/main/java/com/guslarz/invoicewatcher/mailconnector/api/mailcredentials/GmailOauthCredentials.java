package com.guslarz.invoicewatcher.mailconnector.api.mailcredentials;

import com.guslarz.invoicewatcher.secret.api.Secret;
import lombok.Builder;

/** Persisted Gmail grant; access tokens are never part of this record. */
@Builder(toBuilder = true)
public record GmailOauthCredentials(
    String username, String grantId, Secret refreshToken, long revision, boolean reconnectRequired)
    implements MailCredentials {

  @Override
  public <T> T accept(MailCredentialsVisitor<T> visitor) {
    return visitor.visitGmailOauthCredentials(this);
  }

  @Override
  public String toString() {
    return "GmailOauthCredentials[redacted]";
  }
}
