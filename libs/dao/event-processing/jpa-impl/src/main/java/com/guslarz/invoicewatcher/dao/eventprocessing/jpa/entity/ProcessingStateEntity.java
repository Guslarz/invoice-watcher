package com.guslarz.invoicewatcher.dao.eventprocessing.jpa.entity;

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
    name = "event_processing_state",
    indexes = @Index(name = "processing_pending", columnList = "record_kind,stage,pending"))
@Getter
@Setter
@NoArgsConstructor
public class ProcessingStateEntity {
  @Id
  @Column(length = 768)
  private String id;

  @Column(name = "record_kind", nullable = false, length = 16)
  private String kind;

  @Column(nullable = false, length = 256)
  private String stage;

  @Column(nullable = false)
  private boolean pending;

  @Lob
  @Column(nullable = false)
  private String snapshot;

  @Version private long revision;
}
