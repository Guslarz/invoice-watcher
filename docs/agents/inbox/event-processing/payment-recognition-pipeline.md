---
title: Build payment-evidence recognition from mail and payment signals
kind: idea
area: events
added: 2026-11-11
status: later
size: L
blocked-by: [event-processing/mail-event-source-identity, event-processing/mail-attachment-extraction, event-processing/event-processor-operational-foundations]
---

Stage 2 in the [initiative roadmap](README.md) (ADR-0010, ADR-0011, ADR-0012). Implement a
separate payment-recognition consumer/service that emits versioned payment records and evidence,
not invoice payment status. Initially recognize bank e-mails reporting the user's transfers.
Capture amount/currency/date/recipient/reference/transfer identity when known, and preserve the
reported transfer state (executed/booked, pending, scheduled, failed or reversed). Record extraction
confidence separately from source trust; define source authentication beyond a sender-address match.
Trusted executed-transfer records may support paid status after matching; generic claims and
non-executed transfers may not. Deduplicate multiple notifications of the same transfer, including
pending-to-executed updates. Processing must be idempotent, replay-safe and explicit about
unknown/partial fields rather than fabricating values.
