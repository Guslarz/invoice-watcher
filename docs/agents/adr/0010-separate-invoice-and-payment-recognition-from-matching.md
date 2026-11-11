---
id: ADR-0010
status: accepted
date: 2026-11-11
---

# ADR-0010: Separate invoice and payment recognition from matching

## Context

`mail-producer-app` publishes one event per picked-up mail message (SPEC-EVENT). Downstream
processing must turn those messages into invoice and payment information and derive invoice payment
status, but the codebase has no consumers, no recognition logic and no status storage. The design
must also account for current gaps and constraints: message `content` is not read yet
(MAIL-030, `docs/agents/inbox/mail-content-not-read.md`), attachments are out of scope of current
scanning, and the current event carries sender, title, UTC date and a null content field only
(EVENT-004). Published mail events are at-least-once and may arrive again after restarts
(EVENT-012).

Invoice recognition, payment recognition and invoice-payment matching have different evidence
sources, change rates and failure modes. A generic e-mail claiming payment is not equivalent to a
trusted bank notification reporting an executed transfer (ADR-0012). A single mail message may
contain no invoice, one invoice, one payment signal, or
information relevant to several documents.

## Decision

- **Select independent consumers with explicit boundaries.** Build separate recognition stages for
  invoices and for payments, and a separate matching/status stage. Do not build one combined
  recognizer that emits final payment status directly from a mail event.
  - **Invoice recognition** consumes mail-derived events and emits proposed invoice facts and
    extracted evidence (for example issuer, invoice number, amounts, due date, references) with a
    confidence/result state. It does not decide payment status.
  - **Payment recognition** consumes mail-derived or other payment-evidence events and emits proposed
    payment facts (for example payer, amount, currency, payment date, bank/reference text, source)
    as evidence with a confidence/result state. It does not decide that an invoice is paid.
  - **Matching and status projection** consumes recognized invoice and payment facts and produces
    match decisions and a derived invoice payment-status view. It owns reconciliation rules, review
    states and status transitions.
- **Treat the current `all-mails` event as the ingestion source, not the recognition contract.**
  Recognition consumers may read it, but future processors must publish their own explicit result
  events/snapshots for downstream use rather than overloading mail events with mutable
  interpretation.
- **Require an evidence envelope on every recognized fact.** A recognized invoice or payment record
  must carry source references (origin event/topic/key/offset or equivalent, mail/user/folder/message
  identity as available), extraction method/version, confidence, timestamps and raw evidence pointers
  needed for audit and replay. Sensitive message content must not be copied into logs; storage of
  raw content or attachments is a separate, access-controlled design choice per stage.
- **Keep recognition stateless per source document where possible; keep matching stateful.**
  Recognition may cache deduplication markers, but the authoritative reconciliation state
  (known invoices, known payments, applied matches, open review items) belongs to the matching/status
  stage and its storage.
- **Defer the processing runtime choice behind these contracts.** A plain stateful consumer/service
  is the selected starting point for early stages because it is simpler to operate and reason about.
  A stream-processing framework is not selected now; it may be reconsidered when join/window/replay
  requirements are proven and the team can justify the operational cost.
- **Do not assume current events are sufficient.** Missing message content, absent stable source
  identifiers and unsupported attachments are prerequisites for useful recognition and must be
  addressed before implementing invoice/payment extraction stages.

## Consequences

- **Easier:** each stage can evolve, be tested, replayed and reviewed independently; invoice and
  payment extraction can use different rules/models; business-status decisions are auditable in one
  place.
- **Easier:** operational failures are isolated (for example payment parsing changes do not require
  reinterpreting invoices, except through explicit replay).
- **Harder:** more contracts, topics/storage records and observability surfaces to define and
  maintain.
- **Harder:** matching must tolerate incomplete, late or corrected inputs from both sides.
- **Must keep doing:** distinguish proposals/evidence from confirmed accounting facts; document event
  schemas and compatibility before producers and consumers are deployed; treat extraction confidence
  and manual review as first-class outcomes, not exceptions hidden in logs.
