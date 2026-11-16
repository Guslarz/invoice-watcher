---
title: Remove unused code in libs
kind: tech-debt
area: persistence
added: 2026-10-24
status: later
size: S
blocked-by: [event-processing/mail-event-source-identity]
---

`MailProcessorContext.scanConfig` is set but never read. Recheck and remove it after the
event-processing source-identity change, which owns the mail-producer handover, context,
service and their tests. Keep this cleanup separate from that concurrent change.
