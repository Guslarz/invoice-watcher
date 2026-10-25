package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import lombok.Builder;

import java.util.Collection;
import java.util.List;

@Builder(toBuilder = true)
public record MailScanConfig(
    MailServer server,
    MailCredentials credentials,
    Collection<FolderConfig> folders
) {

  public MailScanConfig {
    folders = folders == null ? null : List.copyOf(folders);
  }
}
