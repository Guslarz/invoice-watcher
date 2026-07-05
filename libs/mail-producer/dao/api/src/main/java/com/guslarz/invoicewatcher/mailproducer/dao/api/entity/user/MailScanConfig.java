package com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.MailCredentials;
import lombok.Builder;

import java.util.Collection;

@Builder
public record MailScanConfig(
    MailServer server,
    MailCredentials credentials,
    Collection<FolderConfig> folders
) {
}
