---
title: Align mail-producer naming with the spec vocabulary
kind: tech-debt
area: mail-scanning
added: 2026-10-24
---

One concept, several names:
- The scan is a "task" (`MailProducerTask`), a "scheduler" module and a "processor"
  (`MailProcessorService`, `MailProcessorContext`); the specs say "scan" (MAIL-002) and "publish"
  (EVENT-011). `MailProducerTask` lives alone in `libs/mail-producer/scheduler/api`, while its
  implementation is in `libs/mail-producer/task`, which has no api/impl split.
- The spec's "mailbox configuration" (USER-001/002) is `MailScanConfig` / `scanConfigs`.
- "Email" (`MailConnection.getEmailMessages`, `processEmailMessages`) next to "Mail" everywhere else.
- Domain records (`User`, `MailScanConfig`, `MailServer`, `FolderConfig`) are in
  `...dao.api.entity.user`, although they are not entities and not all about users.
- `MailConnectionProperties` (mail-connector api) is a builder record, not
  `@ConfigurationProperties`, despite the suffix.
- The event topic is `invoice-watcher.mail-producer.task.topic` — an event-publishing setting under
  the task prefix.

Renaming classes is a refactor; moving the property changes configuration and needs Protocol plan.
