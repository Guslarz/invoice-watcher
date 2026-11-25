package com.guslarz.invoicewatcher.dao.mailattachment.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "mail_attachment_captures",
    indexes = @Index(name = "attachment_expiry", columnList = "expires_at"))
@Getter
@Setter
@NoArgsConstructor
public class AttachmentCaptureEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Lob
  @Column(nullable = false)
  private String snapshot;

  @Column(name = "expires_at")
  private Long expiresAt;

  @Version private long revision;
}
