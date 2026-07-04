package com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials;

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
@DiscriminatorValue("basic")
@EqualsAndHashCode(callSuper = true)
public class BasicAuthMailCredentialsEntity extends MailCredentialsEntity {

  private String username;
  private String password;

  public <T> T accept(MailCredentialsEntityVisitor<T> converter) {
    return converter.visitBasicAuthMailCredentialsEntity(this);
  }
}
