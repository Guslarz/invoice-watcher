package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

import java.time.ZonedDateTime;

/**
 * Messages to read from a folder.
 *
 * @param folder Folder name as seen by the mail server.
 * @param startDate Earliest received date of a folder that has never been scanned; {@code null}
 *     means all messages.
 * @param position Position of the previous scan; {@code null} when the folder has never been scanned.
 */
@Builder
public record MailQuery(
    String folder,
    ZonedDateTime startDate,
    ScanPosition position
) {
}
