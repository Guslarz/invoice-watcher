package com.guslarz.invoicewatcher.mailproducer.dao.firestore.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailServerDocument {

  private String host;
  private int port;
}
