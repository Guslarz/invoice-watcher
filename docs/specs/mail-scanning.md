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

<!-- --8<-- [start:schedule] -->
- **MAIL-001** The mail producer runs a scan at a fixed rate, by default every hour. The first
  scan starts when the application starts.
- **MAIL-002** A scan processes every user, every mailbox configuration of that user and every
  configured folder of that mailbox, one after another.
<!-- --8<-- [end:schedule] -->

### Connecting to a mailbox

<!-- --8<-- [start:connection] -->
- **MAIL-010** InvoiceWatcher connects to the mailbox over IMAP with implicit TLS (`imaps`), using
  the host and port from the mailbox configuration (port 993 is the usual one).
- **MAIL-011** It authenticates with a username and password (basic authentication). The password
  is stored encrypted and decrypted only for the moment of connecting
  (see [secret encryption](secret-encryption.md)).
- **MAIL-012** Folders are opened read-only. InvoiceWatcher never marks, moves or deletes
  messages.
<!-- --8<-- [end:connection] -->

### Which messages are picked up

<!-- --8<-- [start:new-messages] -->
- **MAIL-020** Each folder remembers a scan timestamp. A scan picks up the messages in that folder
  sent at or after the scan timestamp.
- **MAIL-021** After the picked-up messages are handed over, the folder's scan timestamp moves
  forward so that the next scan picks up only messages that arrived since. *(gap:
  `docs/agents/inbox/scan-timestamp-moves-to-oldest-message.md`)*
- **MAIL-022** A folder with no new messages is left unchanged and nothing is published for it.
  *(gap: `docs/agents/inbox/folder-search-index-bounds.md`)*
- **MAIL-023** A folder that has never been scanned has no scan timestamp; its first scan picks up
  all messages in the folder. *(gap: `docs/agents/inbox/folder-without-scan-timestamp.md`)*
<!-- --8<-- [end:new-messages] -->

### What is read from a message

- **MAIL-030** For each picked-up message InvoiceWatcher reads the sender, the subject, the sent
  date (converted to UTC) and the content. *(gap: content is not read yet —
  `docs/agents/inbox/mail-content-not-read.md`)*

### Failures

- **MAIL-040** If connecting to a mailbox fails, the error is logged and the scan continues with
  the next mailbox configuration.
- **MAIL-041** If scanning a folder fails, the error is logged; the remaining folders of the same
  mailbox are skipped in this scan and the scan continues with the next mailbox configuration.
  The failed folder's scan timestamp is not changed, so its messages are picked up again next time.

## Acceptance

- **MAIL-001** Given the scan rate `10s`, when the application has been running for 30 seconds,
  then at least three scans have started.
- **MAIL-012** Given a folder with unread messages, when it is scanned, then the messages are
  still unread on the server.
- **MAIL-020** Given a folder with scan timestamp `2026-10-01T00:00Z` and messages sent on
  09-30, 10-01 and 10-02, when it is scanned, then the messages from 10-01 and 10-02 are published.
- **MAIL-021** Given the scan from MAIL-020 has finished and no new mail arrived, when the folder
  is scanned again, then nothing is published.
- **MAIL-022** Given an empty folder, or a folder whose newest message is older than its scan
  timestamp, when it is scanned, then nothing is published and no error is logged.
- **MAIL-040** Given two mailbox configurations where the first has a wrong password, when a scan
  runs, then an error is logged for the first and messages of the second are published.

## Configuration

| Property | Default | Meaning |
|----------|---------|---------|
| `invoice-watcher.mail-producer.scheduler.rate` | `1h` | Time between scans (ISO-8601 or `10s`, `5m`, `1h`) |

## Out of scope

- Recognizing which messages are invoices and extracting data from them — done by consumers of
  the published events.
- Other protocols (POP3, Gmail / Microsoft Graph APIs) and OAuth authentication.
- Attachments.
