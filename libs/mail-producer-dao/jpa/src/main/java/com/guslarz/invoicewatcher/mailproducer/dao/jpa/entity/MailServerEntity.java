package com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class MailServerEntity {

  @Column
  private String host;

  @Column
  private Integer port;
}
