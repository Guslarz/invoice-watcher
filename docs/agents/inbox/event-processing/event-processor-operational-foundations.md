---
title: Define operational foundations for event processors
kind: idea
area: events
added: 2026-11-11
status: later
size: M
blocked-by: [event-processing/mail-event-source-identity]
---

Stage 0 in the [initiative roadmap](README.md). Before building recognition consumers, define
and implement shared operational conventions: correlation/source ids in logs and metrics, per-stage
success/duplicate/retry/quarantine/review/lag measurements, bounded retry policy, quarantine or
dead-letter handling with replay procedure, and sensitive-data logging rules. Keep the solution
consistent with module and deployment conventions; specify it in the relevant future spec/ADR.
