package com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import java.util.Collection;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record MailScanConfig(
    MailServer server, MailCredentials credentials, Collection<FolderConfig> folders) {

  public MailScanConfig {
    folders = folders == null ? null : List.copyOf(folders);
  }
}
