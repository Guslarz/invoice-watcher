---
title: Scan timestamp is set to the oldest picked-up message
kind: bug
area: mail-scanning
added: 2026-10-13
---

Violates MAIL-021. `MailProducerTaskImpl.processEmailMessages` calls
`folderConfigDaoService.updateScanTimestamp(..., mailMessages.getFirst().date())`. Messages come
oldest first, so the timestamp is set to the *oldest* message of the batch, and because MAIL-020
is inclusive (`sent >= timestamp`) the next scan picks up the whole batch again. Every scan
re-publishes the same messages until the folder is reset.

Fix: use the newest date (`getLast()` or max) and make the next query exclusive, or store the
last processed message's UID (IMAP `UIDVALIDITY` + `UID`) instead of a date — dates are set by
senders and are not monotonic.
