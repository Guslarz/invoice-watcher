package com.guslarz.invoicewatcher.mailproducer.dao.firestore.document;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderConfigDocument {

  @DocumentId
  private String id;

  private String name;
  private String scanTimestamp;
}
