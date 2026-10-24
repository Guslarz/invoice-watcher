package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

/**
 * A message together with its IMAP UID in the folder.
 */
@Builder
public record FolderMessage(
    long uid,
    MailMessage message
) {
}
