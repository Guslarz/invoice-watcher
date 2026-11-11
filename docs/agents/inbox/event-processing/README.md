# Event-processing roadmap

Temporary roadmap for turning mail events into invoice and payment information and deriving invoice
payment status. This document is a design and delivery plan, not implemented behavior: it does not
change current specs, event contracts, configuration, applications or infrastructure.

Update this overview as stages progress. Delete completed notes with their implementation PRs;
delete this directory when the initiative is complete or abandoned, after preserving lasting
behavior and decisions in specs, ADRs and user documentation. Deleting all notes alone does not
establish that the initiative's goals have been met.

## Current baseline

- Mail scanning publishes one at-least-once event per picked-up message to a single Kafka topic
  (SPEC-MAIL, SPEC-EVENT). The event key is the user e-mail address; the value contains `sender`,
  `title`, `date`, `content` and `bodyMediaType`.
- Message bodies are read according to MAIL-030–032 in the
  [mail-scanning spec](../../specs/mail-scanning.md); attachments remain out of scope of scanning.
- There are no downstream consumers, recognition modules, matching logic or payment-status storage.

## Selected direction

Recorded in the [ADR index](../../adr/index.md); open each record for rationale:

- [ADR-0010](../../adr/0010-separate-invoice-and-payment-recognition-from-matching.md): separate invoice
  recognition, payment recognition, and matching/status projection; keep matching stateful and treat
  the current mail event as ingestion input, not the recognition contract.
- [ADR-0011](../../adr/0011-identify-documents-and-payments-by-stable-business-keys.md): use stable
  tenant-scoped source/business/processing identities, idempotent upsert processing, explicit schema
  versions and replayable state reconstruction.
- [ADR-0012](../../adr/0012-derive-invoice-payment-status-from-evidence-and-matching.md): derive status
  from accepted payment sources and auditable matches; trusted bank notifications of executed
  transfers may support paid status, unlike generic payment claims;
  make ambiguous, partial, unmatched and conflicting cases explicit reviewable states.

Selected starting runtime: plain stateful Spring consumers/services consistent with existing module
conventions (ADR-0003), with durable idempotency/state storage per stage. A stream-processing
framework (Kafka Streams/Flink or similar) is **not selected**; reconsider only with evidence that
join/window/latency requirements justify its operational cost.

## Boundaries and data flow

```text
mail-producer-app
  -> Kafka topic: mail ingestion events (current: all-mails, at-least-once)

invoice recognition consumer
  -> reads mail-derived input
  -> emits invoice-recognition results (facts + evidence + confidence/review state)

payment recognition consumer
  -> reads mail-derived/payment-evidence input
  -> emits payment-recognition results (evidence + confidence/review state)

matching and status projection
  -> reads invoice and payment results (+ manual decisions/corrections later)
  -> stores applied matches and current invoice status projection
  -> exposes review items and explainable status for operators/users later
```

Each boundary publishes explicit result contracts; downstream stages do not infer mutable meaning by
re-reading raw mail events except through defined replay/reprocessing flows. Every result carries an
evidence envelope (source refs, extraction/match method and version, confidence, timestamps) without
logging sensitive content.

## Identity, idempotency and state reconstruction

- **Duplicates/replay:** all consumers must be idempotent on stable keys (ADR-0011). Kafka offset is
  not a business identity. Replaying a topic or reprocessing history must produce the same effective
  state for the same processing version, or a new explicitly versioned state.
- **Ordering:** accept late, duplicated and out-of-order inputs. Matching/projection must record
  events and recalculate or flag review when business order cannot be inferred safely; do not assume
  arrival order equals invoice/payment chronology.
- **State reconstruction:** recognized facts, matches and projections must be rebuildable from
  retained events and/or latest keyed materialized records. No stage may rely on non-durable
  in-memory state for correctness.
- **Schema evolution:** additive, versioned payloads/records by default; unknown fields ignored by
  consumers that do not need them. Breaking changes require a new version/type and replay/migration
  plan.

## Evidence, matching and status handling

- **Accepted payment sources:** the initial workflow uses trusted bank e-mails reporting executed/
  booked transfers. These may automatically support paid or partially-paid status after matching;
  no bank API or manual confirmation for every transfer is required. "Paid" describes support from
  accepted records, not guaranteed final settlement. Define source authentication beyond a matching
  sender address, separately from extraction confidence. Generic payment claims and scheduled,
  pending or failed transfers remain evidence only and do not contribute to paid totals.
- **Transfer matching:** use amount, currency, recipient and invoice/reference information to
  identify invoices sufficiently; leave ambiguous matches unresolved. Count a transfer once across
  duplicate or pending-to-executed notifications using transfer identity, not just message identity.
- **Partial/multiple payments:** allocate amounts per invoice, track remaining balance, allow one
  payment to cover multiple invoices, and surface over-allocation as conflict/review.
- **Unmatched/ambiguous:** low confidence, missing references, multiple candidates, identity
  ambiguity or conflicts produce review items with reasons/candidates; do not guess merges.
- **Currencies/corrections/reversals:** currency, FX, tolerance and rounding rules block automatic
  full settlement until defined; corrections, refunds, chargebacks and reversals are new facts that
  supersede prior effects and trigger recalculation.

## Prerequisites

These must be assessed and delivered before the stage that depends on them; they are not implicitly
included in this roadmap change.

| Prerequisite | Why | Tracking |
|---|---|---|
| Read useful message content | Invoice/payment recognition needs evidence beyond sender/title/date | Implemented: MAIL-030–032 in the [mail-scanning spec](../../specs/mail-scanning.md); attachments remain a separate prerequisite |
| Stable mail source identity in ingestion input | Source-level dedup/replay for recognition (ADR-0011) | [`mail-event-source-identity.md`](mail-event-source-identity.md) |
| Attachment extraction/access strategy | Many invoices and remittance advices are attachments; sensitive-data handling required | [`mail-attachment-extraction.md`](mail-attachment-extraction.md) |
| Event schema/compatibility convention for new topics/records | Safe evolution across producers/consumers (ADR-0011) | Stage 0 below |
| Source authentication, transfer-state mapping, matching rules, currency/FX/tolerance and manual-review ownership | Safely derive paid status from accepted executed-transfer records (ADR-0012) | Specify per stage; bank e-mails are the selected initial source |

## Stages

Each stage is independently reviewable and must ship with specs, tests, docs/ops updates as
applicable, idempotency coverage, and observability for the new behavior.

### Stage 0 — Contracts and operational foundations

**Goal:** make future processors buildable without guessing identities or contracts.

**Deliverables:** define versioned result/event/record conventions (naming, schema version field,
compatibility rules), source-id strategy for mail input, tenant keying/partitioning rules, and
baseline processor operations (correlation ids, metrics, structured logs without sensitive content,
retry policy, quarantine/dead-letter destination and replay procedure).

**Acceptance goals:** a proposed contract for one recognition result can be reviewed against
ADR-0010/0011; duplicate input processing is defined as idempotent upsert; a failed record can be
quarantined and replayed without manual database surgery; no raw credentials or unnecessary message
body/attachment content appears in logs.

**Tracking:** [`mail-event-source-identity.md`](mail-event-source-identity.md),
[`event-processor-operational-foundations.md`](event-processor-operational-foundations.md).

### Stage 1 — Invoice recognition

**Goal:** produce reviewable invoice facts from ingested mail evidence.

**Deliverables:** invoice-recognition consumer/service, durable deduplication/state, invoice-result
contract, extraction pipeline (rules-based first where feasible; AI-assisted extraction only as an
explicit, evaluated implementation choice with cost/privacy/quality controls), and review output for
low-confidence or ambiguous documents.

**Acceptance goals:** replaying the same source evidence does not create duplicate invoice records;
extracted fields include source evidence refs and confidence; ambiguous identity or low confidence is
marked for review rather than merged; unsupported content/attachments fail safely into a visible
non-success/review outcome.

**Depends on:** prerequisites for content/source identity, Stage 0. **Tracking:**
[`invoice-recognition-pipeline.md`](invoice-recognition-pipeline.md).

### Stage 2 — Payment recognition

**Goal:** recognize reported transfers and other payment evidence, not invoice payment status.

**Deliverables:** payment-recognition consumer/service, payment-evidence contract, extraction of
amount/currency/date/recipient/reference/transfer identity where present, explicit reported transfer
states and source-trust metadata. Start with bank notification e-mails for the user's transfers.

**Acceptance goals:** distinguish trusted bank notifications of executed/booked transfers from
generic claims, unverified sources and scheduled/pending/failed transfers (ADR-0012); validate source
trust independently of extraction confidence and not by sender-address match alone; duplicate and
pending-to-executed notifications do not create additional transfers; unknown/partial payment fields
produce reviewable records, not fabricated values.

**Depends on:** Stage 0 and relevant content/attachment prerequisites. **Tracking:**
[`payment-recognition-pipeline.md`](payment-recognition-pipeline.md).

### Stage 3 — Matching and status projection

**Goal:** derive explainable invoice payment status from invoice facts and payment evidence.

**Deliverables:** stateful matching/projector, match-decision and status-projection storage/query
model, allocation logic for partial/multiple payments, correction/reversal handling, and review queue
states for unmatched/conflicting/ambiguous cases.

**Acceptance goals:** status can be explained by the exact evidence and matches that produced it;
partial and multi-invoice allocations are represented without losing remainder/conflict information;
corrections/reversals recalculate status; duplicate/out-of-order inputs converge to the same
effective projection for the same rule version; a sufficiently matched trusted bank notification of
an executed transfer automatically yields paid or partially paid according to allocation, without
a bank API or per-transfer manual confirmation; generic claims, unverified sources and non-executed
transfers do not increase paid totals; reversals reopen the affected balance.

**Depends on:** Stages 1-2 and specified source-trust, matching and currency rules. **Tracking:**
[`invoice-payment-matching-and-status.md`](invoice-payment-matching-and-status.md).

### Stage 4 — Product and operations integration (later)

**Goal:** expose and operate the capability safely for users/operators.

**Possible deliverables:** review UI/workflow, stronger confirmation sources, reporting/query APIs,
retention and access controls for sensitive extracted data, alerting/runbooks, and performance/cost
tuning. Specific scope requires separate plans/specs.

## Cross-cutting concerns

- **Storage/query projections:** each stateful stage needs durable storage suitable for keyed upserts,
  lookup by tenant/business identity, and review/status queries. The specific technology (Firestore,
  relational/JPA, or other) is **not selected** here; choose per stage based on query needs,
  transaction/upsert semantics, consistency, cost and existing deployment constraints.
- **Retries:** transient failures retry with bounded backoff; retries must be safe under idempotency
  keys. Non-retryable/poison records move to quarantine/dead-letter with diagnostics and source refs.
- **Observability:** per-stage counters for consumed/produced/succeeded/duplicate/ retried/
  quarantined/review items, processing latency, lag, and last-success timestamps; alerts on lag,
  quarantine growth and repeated review causes. Never log credentials or unnecessary sensitive content.
- **Replay:** support controlled replay from Kafka offsets/time windows and/or reprocessing from stored
  source evidence with a declared processing version; document the blast radius and idempotency
  expectations before running.
- **Sensitive data:** mail bodies, attachments and extracted financial data may contain personal and
  confidential information. Apply least-privilege access, avoid raw content in logs/metrics, define
  retention/redaction/encryption expectations in stage specs, and treat AI-assisted extraction as a
  data-handling decision requiring explicit approval.

## Open questions and evidence needed

- Authentication and account association for each supported bank e-mail source; mapping its wording
  to transfer states and stable transfer identifiers.
- Matching sufficiency rules and manual-resolution workflow for ambiguous or unsupported evidence.
- Currency conversion, FX source, rounding/tolerance and partial-settlement business rules.
- Canonical invoice identity when supplier invoice numbers are missing, malformed or reused.
- Whether attachments must be stored durably for audit/reprocessing or only processed transiently.
- Rules-based vs AI-assisted extraction per document type, including quality/cost/privacy evaluation.
- Storage technology per stage and whether one shared projection store is warranted.
- Whether/when stream processing is justified over plain consumers.

## Plan acceptance mapping

- **Boundaries and data flow:** see "Boundaries and data flow" and ADR-0010.
- **Identity, idempotency and state reconstruction:** see "Identity, idempotency and state
  reconstruction" and ADR-0011.
- **Partial/unmatched/ambiguous handling:** see "Evidence, matching and status handling" and
  ADR-0012.
- **Prerequisites, stages and acceptance goals:** see "Prerequisites" and "Stages".
- **No implementation changes in this change:** this roadmap, ADRs and linked inbox notes only.
