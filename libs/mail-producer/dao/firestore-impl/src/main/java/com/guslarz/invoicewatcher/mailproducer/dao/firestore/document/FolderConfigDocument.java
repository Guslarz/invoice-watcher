package com.guslarz.invoicewatcher.mailproducer.dao.firestore.document;

import com.google.cloud.spring.data.datastore.core.mapping.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "mail-producer-folders")
public class FolderConfigDocument {

  @Id
  private String id;

  private String name;
  private String startDate;
  private Long uidValidity;
  private Long lastUid;
}
