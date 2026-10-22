---
title: Resume a folder after the last delivered message
kind: idea
area: events
added: 2026-10-22
---

Related to EVENT-012. When delivery fails on the n-th message of a folder, the scan timestamp is
not moved at all, so the next scan publishes messages 1..n-1 again. Moving the scan timestamp
after every confirmed event (instead of once per folder) would resume at the failed message and
avoid those duplicates.

Costs: one storage write per message instead of one per folder; relies on messages being
handed over oldest first (EVENT-001) and on the timestamp being set correctly — blocked by
`scan-timestamp-moves-to-oldest-message.md`. Messages with the same sent date can still be
duplicated or skipped at the boundary; storing the last processed IMAP UID instead of a date
(suggested in that note) would solve both.
