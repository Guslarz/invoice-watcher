package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DiscriminatorValue("gmail-oauth")
@EqualsAndHashCode(callSuper = true)
public class GmailOauthCredentialsEntity extends MailCredentialsEntity {

  private String gmailUsername;
  private String grantId;

  @Column(length = 4096)
  private String refreshToken;

  private long grantRevision;
  private boolean reconnectRequired;

  @Override
  public <T> T accept(MailCredentialsEntityVisitor<T> visitor) {
    return visitor.visitGmailOauthCredentialsEntity(this);
  }
}
