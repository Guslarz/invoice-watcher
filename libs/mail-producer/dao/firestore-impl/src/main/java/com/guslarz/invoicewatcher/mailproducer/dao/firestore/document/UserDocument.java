package com.guslarz.invoicewatcher.mailproducer.dao.firestore.document;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

  @DocumentId
  private String id;

  private String name;
  private List<MailScanConfigDocument> scanConfigs;
}
