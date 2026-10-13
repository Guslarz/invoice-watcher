---
title: Dev overlay sets a scheduler rate property that does not exist
kind: bug
area: deployment
added: 2026-10-13
---

`deployments/k8s-apps/apps/mail-producer/overlays/dev/config/.env` sets
`INVOICE_WATCHER_EVENT_PRODUCER_SCHEDULER_RATE=10s`, but the property is
`invoice-watcher.mail-producer.scheduler.rate` (`MailProducerSchedulerProperties`), i.e.
`INVOICE_WATCHER_MAIL_PRODUCER_SCHEDULER_RATE`. Dev therefore runs with the default rate `1h`.
