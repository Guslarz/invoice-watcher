---
id: SPEC-MAIL
title: Mail scanning
status: implemented
audience: [user, operator, developer]
modules:
  - libs/mail-connector/api
  - libs/mail-connector/impl
  - libs/mail-producer/scheduler/api
  - libs/mail-producer/scheduler/impl
  - libs/mail-producer/task
---

# Mail scanning

## Purpose

InvoiceWatcher periodically reads the mailboxes its users have configured and hands every new
message to [event publishing](event-publishing.md). A user does not have to forward anything:
it is enough to tell InvoiceWatcher which server, account and folders to watch
(see [user configuration](user-configuration.md)).

## Behavior

### Schedule

- **MAIL-001** The mail producer runs a scan at a fixed rate, by default every hour. The first
  scan starts when the application starts. The rate
  (`invoice-watcher.mail-producer.scheduler.rate`) is at least one second; a smaller value fails
  the startup and names the property.
- **MAIL-002** A scan processes every user, every mailbox configuration of that user and every
  configured folder of that mailbox, one after another.

### Connecting to a mailbox

- **MAIL-010** InvoiceWatcher connects to the mailbox over IMAP with implicit TLS (`imaps`), using
  the host and port from the mailbox configuration (port 993 is the usual one). The server's TLS
  certificate must be trusted and issued for the configured host; otherwise connecting fails
  (MAIL-040).
- **MAIL-011** It authenticates with a username and password (basic authentication). The password
  is stored encrypted and decrypted only for the moment of connecting
  (see [secret encryption](../specs/secret-encryption.md)).
- **MAIL-012** Folders are opened read-only. InvoiceWatcher never marks, moves or deletes
  messages.
- **MAIL-013** Connecting gives up after `invoice-watcher.mail-connector.imap.connect-timeout`
  (default 30 s), and every wait for a server response gives up after
  `invoice-watcher.mail-connector.imap.read-timeout` (default 60 s). A timeout while connecting is
  handled as in MAIL-040, during a folder scan as in MAIL-041. Both are at least one second; a
  smaller value fails the startup and names the property.

### Which messages are picked up

- **MAIL-020** Each folder keeps a scan position: the folder's IMAP UIDVALIDITY and the UID of the
  last handed-over message. A scan picks up the messages with a higher UID, in UID order (the order
  the server received them).
- **MAIL-021** After the picked-up messages are handed over, the position moves to the last of
  them, so the next scan picks up only messages that arrived since.
- **MAIL-022** A folder with no new messages is left unchanged and nothing is published for it.
- **MAIL-023** A folder without a scan position has never been scanned. Its first scan picks up the
  messages the server received at or after the folder's start date, or all messages when the
  folder has no start date.
- **MAIL-024** When the folder's UIDVALIDITY differs from the stored one (the server renumbered the
  folder), the position is discarded and the folder is scanned as if it had never been scanned.

### What is read from a message

- **MAIL-030** For each picked-up message InvoiceWatcher reads the sender, the subject, the sent
  date (converted to UTC) and the content. *(gap: content is not read yet —
  `docs/agents/inbox/mail-content-not-read.md`)*

### Failures

- **MAIL-040** If connecting to a mailbox fails, the error is logged and the scan continues with
  the next mailbox configuration.
- **MAIL-041** If scanning a folder fails, the error is logged; the remaining folders of the same
  mailbox are skipped in this scan and the scan continues with the next mailbox configuration.
  The failed folder's position moves to the last message handed over before the failure, so the
  next scan continues with the first one that was not.

## Acceptance

- **MAIL-001** Given the scan rate `10s`, when the application has been running for 30 seconds,
  then at least three scans have started. Given the scan rate `0s`, when the application starts,
  then it fails with an error naming `invoice-watcher.mail-producer.scheduler.rate`.
- **MAIL-010** Given an IMAPS server whose certificate is trusted but issued for another host,
  when a connection is made to it, then connecting fails.
- **MAIL-012** Given a folder with unread messages, when it is scanned, then the messages are
  still unread on the server.
- **MAIL-013** Given a server that accepts TCP connections and never answers, and a read timeout
  of 1 s, when a connection is made, then it fails within a few seconds. Given the read timeout
  `0s`, when the application starts, then it fails with an error naming
  `invoice-watcher.mail-connector.imap.read-timeout`.
- **MAIL-020** Given a folder with position (V, 12) and messages with UIDs 10–15, when it is
  scanned, then the messages 13, 14 and 15 are published in that order — also when message 14 has
  an older sent date than message 13.
- **MAIL-021** Given the scan from MAIL-020 has finished and no new mail arrived, when the folder
  is scanned again, then nothing is published.
- **MAIL-022** Given an empty folder, or a folder whose last UID equals its position, when it is
  scanned, then nothing is published, the position is not written and no error is logged.
- **MAIL-023** Given a folder without position and start date `2026-10-01T00:00Z`, with messages
  received on 09-30 and 10-02, when it is scanned, then only the 10-02 message is published.
  Without a start date, both are published.
- **MAIL-024** Given a stored UIDVALIDITY V while the server reports W, when the folder is scanned,
  then it is scanned as never scanned and the position is saved with W.
- **MAIL-040** Given two mailbox configurations where the first has a wrong password, when a scan
  runs, then an error is logged for the first and messages of the second are published.

## Configuration

Properties, their types, defaults and meaning are listed in the generated reference:

- [`invoice-watcher.mail-producer.scheduler`](../../reference/configuration.md#invoice-watcher-mail-producer-scheduler)
- [`invoice-watcher.mail-connector.imap`](../../reference/configuration.md#invoice-watcher-mail-connector-imap)

## Out of scope

- Recognizing which messages are invoices and extracting data from them — done by consumers of
  the published events.
- Other protocols (POP3, Gmail / Microsoft Graph APIs) and OAuth authentication.
- Attachments.
