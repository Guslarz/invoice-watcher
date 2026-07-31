package com.guslarz.invoicewatcher.mailproducer.dao.firestore.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailScanConfigDocument {

  private MailServerDocument server;
  private MailCredentialsDocument credentials;
  private List<String> folderIds;
}
