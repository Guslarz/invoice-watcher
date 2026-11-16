package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document;

import com.google.cloud.spring.data.datastore.core.mapping.Entity;
import com.google.cloud.spring.data.datastore.core.mapping.Unindexed;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class MailCredentialsDocument {

  private String type;
  private String username;
  @Unindexed private String password;
  private String grantId;
  @Unindexed private String refreshToken;
  private Long grantRevision;
  private Boolean reconnectRequired;

  public <T> T accept(MailCredentialsDocumentVisitor<T> visitor) {
    return switch (type) {
      case null -> visitor.visitBasicAuthMailCredentialsDocument(this);
      case "basic" -> visitor.visitBasicAuthMailCredentialsDocument(this);
      case "gmail-oauth" -> visitor.visitGmailOauthCredentialsDocument(this);
      default -> throw new IllegalArgumentException("Unknown credentials type: " + type);
    };
  }
}
