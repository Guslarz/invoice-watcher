package com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity;

import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials.MailCredentialsEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

  @Id
  @GeneratedValue
  private Long id;

  @Column(unique = true)
  private String name;

  @Embedded
  private MailServerEntity mailServer;

  @OneToOne(cascade = CascadeType.REMOVE)
  private MailCredentialsEntity mailCredentials;
}
