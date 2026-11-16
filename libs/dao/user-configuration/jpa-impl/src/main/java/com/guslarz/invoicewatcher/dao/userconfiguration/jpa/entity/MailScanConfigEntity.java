package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity;

import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials.MailCredentialsEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailScanConfigEntity {

  @Id @GeneratedValue private Long id;

  @Embedded private MailServerEntity server;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  private MailCredentialsEntity credentials;

  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  private Collection<FolderConfigEntity> folders;
}
