package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderConfigEntity {

  @Id @GeneratedValue private Long id;

  @Column private String name;

  @Column private ZonedDateTime startDate;

  @Column private Long uidValidity;

  @Column private Long lastUid;
}
