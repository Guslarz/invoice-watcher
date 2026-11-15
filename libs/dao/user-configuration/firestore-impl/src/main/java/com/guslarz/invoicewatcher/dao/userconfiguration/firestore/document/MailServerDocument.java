package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document;

import com.google.cloud.spring.data.datastore.core.mapping.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class MailServerDocument {

  private String host;
  private int port;
}
