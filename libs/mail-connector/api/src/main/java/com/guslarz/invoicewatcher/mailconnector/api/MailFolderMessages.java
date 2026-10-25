package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

import java.util.List;

/**
 * Messages read from a folder, in UID order.
 *
 * @param uidValidity Current IMAP UIDVALIDITY of the folder.
 * @param messages Messages matching the query, ordered by UID.
 */
@Builder
public record MailFolderMessages(
    long uidValidity,
    List<FolderMessage> messages
) {

  public MailFolderMessages {
    messages = messages == null ? null : List.copyOf(messages);
  }
}
