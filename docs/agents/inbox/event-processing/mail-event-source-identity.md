---
title: Add stable source identity to mail ingestion events
kind: tech-debt
area: events
added: 2026-11-11
status: ready
size: M
blocked-by: []
---

Prerequisite for invoice/payment recognition (ADR-0011, [initiative roadmap](README.md)).
The current `all-mails` event (EVENT-004) has no stable source-message identifier, so downstream
recognition cannot safely deduplicate replays or re-ingestion by source evidence. Define and add a
tenant-scoped, immutable source identity (for example user/mailbox/folder plus IMAP UIDVALIDITY/UID
or provider-specific id) to the mail ingestion contract, with schema-version/compatibility handling
and spec/test updates. Coordinate with consumers and any existing topic compatibility expectations.
