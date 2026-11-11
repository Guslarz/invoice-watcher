---
title: Build invoice recognition from mail evidence
kind: idea
area: events
added: 2026-11-11
status: later
size: L
blocked-by: [mail-content-not-read, event-processing/mail-event-source-identity, event-processing/mail-attachment-extraction, event-processing/event-processor-operational-foundations]
---

Stage 1 in the [initiative roadmap](README.md) (ADR-0010, ADR-0011). Implement a separate
invoice-recognition consumer/service that reads mail-derived input and emits versioned invoice facts
with source evidence refs, extraction method/version and confidence. Use idempotent keyed upserts,
handle ambiguous/low-confidence results as review states, and fail safely for unsupported content.
Evaluate rules-based extraction first; treat AI-assisted extraction as an explicit, approved
data-handling and quality/cost choice in the implementing plan/spec. Depends on available message
content, stable source identity and attachment strategy.
