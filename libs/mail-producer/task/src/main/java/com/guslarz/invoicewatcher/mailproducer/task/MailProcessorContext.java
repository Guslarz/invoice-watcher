package com.guslarz.invoicewatcher.mailproducer.task;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.MailScanConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import lombok.Builder;

@Builder(toBuilder = true)
public record MailProcessorContext(
    User user,
    MailScanConfig scanConfig,
    FolderConfig folderConfig
) {
}
