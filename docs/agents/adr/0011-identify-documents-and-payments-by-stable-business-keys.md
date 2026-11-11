---
id: ADR-0011
status: accepted
date: 2026-11-11
---

# ADR-0011: Identify documents and payments by stable business keys

## Context

Event delivery is at-least-once (EVENT-012) and consumers must tolerate duplicates. Future invoice
and payment pipelines will consume replayed, duplicated and out-of-order events from Kafka and may
reprocess historical sources during backfills or bug fixes. Mail scanning already relies on stable
IMAP identity within UIDVALIDITY (ADR-0009), but recognized business entities (an invoice, a payment
evidence record, a match) need identities that survive consumer retries, topic replays and source
re-ingestion.

The current mail event does not carry an explicit stable source-message identifier; it carries a
user key, sender, title, date and (currently null) content. Without a defined identity strategy,
duplicate or reordered processing can create duplicate invoices/payments or corrupt derived status.

## Decision

- **Assign every produced record a stable idempotency key and use it for deduplication.** Consumers
  must process an input as an upsert/effect-once operation keyed by that identity, not as an append
  that assumes exactly-once delivery.
- **Use layered identities, not Kafka offset alone.**
  - **Source identity** identifies the originating evidence (for example a specific mail message or
    external payment notification). It is the preferred deduplication key for recognition. A
    prerequisite for mail-driven stages is to add a stable source identifier to mail-derived input
    (e.g. user/mailbox/folder plus UIDVALIDITY/UID or another immutable provider id) because
    sender/title/date/content is not stable enough.
  - **Business identity** identifies the domain object inferred from evidence: for invoices, a
    tenant-scoped natural key such as issuer + invoice number (plus series/year/currency where needed)
    or a deterministic surrogate derived from normalized extracted fields when a number is absent; for
    payments, a tenant-scoped provider/reference id when available, otherwise a deterministic
    fingerprint of stable payment attributes and source evidence. When identity is ambiguous, emit a
    review-required record with a source-scoped identity rather than guessing a merge.
  - **Processing identity** identifies a particular extraction/matching decision version
    (input id + schema/logic version) so reprocessing with new logic is explicit and auditable.
- **Make state reconstruction possible from durable records.** Recognized facts, match decisions and
  status projections must be rebuildable by replaying retained events and/or by reading the latest
  materialized state written with the same idempotency keys. Reprocessing must not depend on
  in-memory consumer state.
- **Handle ordering explicitly.** Matching and projection logic must accept late-arriving,
  duplicated and out-of-order invoices, payments, corrections and reversals. Apply commutative/
  version-aware updates where possible; otherwise record the event and mark the affected status for
  recalculation/review instead of assuming arrival order equals business order.
- **Treat schema evolution as additive and versioned.** Proposed event payloads and stored records
  must include an explicit schema/version marker. Additive, backward-compatible changes are the
  default; breaking changes require a new event/type/version and a migration/replay plan. Producers
  and consumers must ignore unknown fields they do not need and must not rely on undocumented field
  meaning.
- **Scope every identity by tenant/user.** Keys must include the owning user/tenant context so data
  from different users can never collide, even when external identifiers look similar.

## Consequences

- **Easier:** safe retries, Kafka replays, backfills and consumer restarts; deterministic debugging
  because each output points to the input and rule version that produced it.
- **Easier:** parallel processing within a tenant partitioning strategy once keys are well defined.
- **Harder:** identity normalization and ambiguous-document handling require careful rules and tests;
  deduplication stores/indexes add storage and operational cost.
- **Harder:** deterministic fingerprints can change if normalization rules change, so normalization
  rules must be versioned as part of processing identity.
- **Must keep doing:** require idempotency tests for every consumer; define partitioning/sharding by
  tenant and business key before production rollout; treat "cannot identify safely" as a review state,
  not as a best-effort merge.