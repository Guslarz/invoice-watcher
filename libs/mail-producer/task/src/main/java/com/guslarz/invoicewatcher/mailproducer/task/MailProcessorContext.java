package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import lombok.Builder;

@Builder(toBuilder = true)
public record MailProcessorContext(
    User user,
    MailScanConfig scanConfig,
    FolderConfig folderConfig
) {
}
