package com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DiscriminatorColumn(name = "type")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class MailCredentialsEntity {

  @Id
  @GeneratedValue
  private Long id;

  public abstract <T> T accept(MailCredentialsEntityVisitor<T> converter);
}
