package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

/**
 * Last handed-over message of a folder.
 *
 * @param uidValidity IMAP UIDVALIDITY of the folder the UID belongs to.
 * @param lastUid IMAP UID of the last handed-over message.
 */
@Builder
public record ScanPosition(
    long uidValidity,
    long lastUid
) {
}
