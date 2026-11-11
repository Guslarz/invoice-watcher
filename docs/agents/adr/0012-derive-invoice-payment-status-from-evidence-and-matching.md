---
id: ADR-0012
status: accepted
date: 2026-11-11
---

# ADR-0012: Derive invoice payment status from evidence and matching

## Context

The goal is to determine whether an invoice has been paid. Inputs will come from recognized invoice
documents and recognized payment signals, which may be incomplete, duplicated, delayed, contradictory
or based on untrusted text (for example an e-mail stating "we paid"). The initial payment source
is bank notification e-mails for the user's transfers, not a bank API. Product and accounting rules
for source validation, tolerance and manual resolution still need implementing specifications.

A design that collapses "payment mentioned" into "invoice paid" would produce false positives that
are hard to detect and reverse. Conversely, requiring perfect automated proof for every case would
leave most invoices unresolved.

## Decision

- **Separate evidence, matching and status.**
  - **Payment evidence** is any recognized signal that money may have moved or been promised/claimed
    (mail text, remittance advice, bank notification, manual input). Evidence has a source,
    confidence, amount/currency/date/reference fields when known, and a status such as proposed,
    corroborated or rejected. Payment recognition records what the source reports about the transfer
    (executed/booked, pending, scheduled, failed or reversed), separately from extraction confidence
    and source trust. It does not decide which invoice has been paid.
  - **A match** links payment evidence to one or more invoices (or marks it unmatched/ambiguous) with
    an explanation, score/confidence and allocator (amount applied per invoice). Matches may be
    automatic or manual and may be superseded by a later correction.
  - **Invoice payment status** is a derived projection computed from the current invoice facts and
    eligible applied matches under explicit source-trust rules. It is not authored directly by
    recognition; non-rejected evidence alone is not necessarily eligible for payment allocation.
- **Use explicit, reviewable status outcomes.** The selected direction is a small set of derived
  states that distinguish knowledge from settlement, at minimum covering: no payment evidence,
  payment evidence observed but unmatched, partially paid, paid in full (based on available
  evidence), overpaid/conflict, and needs review/ambiguous. Exact enum names are a contract detail
  for the implementing spec; the design requires that ambiguous and evidence-only cases are visible
   states, never silent "paid".
- **Accept trusted bank notifications of executed transfers as payment records.** A bank e-mail
  reporting an executed/booked transfer may support automatic paid or partially-paid status after
  matching. A bank API, additional corroboration and manual confirmation are not prerequisites for
  this initial workflow. "Paid" means supported by an accepted source and matching rules, not an
  absolute guarantee of final settlement.
- **Define source trust separately from recognition confidence.** Before automatic allocation,
  specify how bank notifications are authenticated and associated with the user's account; a
  matching display name or `From` address alone is insufficient. Unsupported or unverified sources
  remain evidence for review. High extraction confidence does not establish source authenticity.
- **Exclude generic claims and non-executed transfers from paid totals.** An arbitrary "we paid"
  e-mail or a scheduled, pending or failed transfer cannot establish payment. Recognition preserves
  these states for review or later updates rather than promoting them to executed payments.
- **Match executed payments before deriving invoice status.** Amount, currency, recipient and
  invoice/reference information must identify the invoice sufficiently under the matching rules.
  Ambiguous matches remain unresolved. Count each transfer once even if the bank sends duplicate
  notifications or separate pending and executed notifications; use transfer identity, not just
  source-message identity. Full eligible allocation yields paid; a smaller allocation yields
  partially paid.
- **Support partial, multiple and cross-invoice payments explicitly.** Matching must allocate amounts
  across one or more invoices, track remaining invoice balance, and keep payment evidence linkable to
  several invoices when a single transfer covers multiple documents. Over-allocation produces a
  conflict/review state, not silent truncation.
- **Record currency and amount handling as matching inputs.** Amount comparisons require currency;
  cross-currency settlement, exchange-rate source, rounding and tolerance are unresolved product/
  accounting rules and must block automatic full-payment conclusions when not satisfied. Until then,
  non-matching currency is a review/conflict case.
- **Model corrections, reversals and retractions as new facts.** Do not mutate history in place.
  A corrected invoice, chargeback, refund, reversed transfer or rejected evidence emits a new
  record/event that supersedes or negates prior effects; the projector recalculates status from the
  current effective set.
- **Require manual resolution for ambiguity.** Low-confidence extraction, multiple candidate matches,
  missing references, currency mismatch, or conflicting evidence must produce a review item with the
  candidates and reasons. Manual decisions are stored as auditable facts/overrides and feed the same
  projection logic.

## Consequences

- **Easier:** bank e-mails can drive automatic paid status without a bank API or manual approval
  for every transfer; users/operators can see the accepted source and allocation behind each status.
- **Easier:** future integrations (bank/accounting/manual confirmation) can add stronger evidence
  without changing recognition boundaries.
- **Harder:** the system must store more state and expose review workflows/projections, not just a
  boolean paid flag.
- **Harder:** source authentication, transfer-state interpretation, transfer deduplication and
  currency/tolerance rules need explicit specifications and tests before automatic paid conclusions.
- **Must keep doing:** every projected status must be explainable by the evidence and matches that
  produced it; reporting and alerts must treat needs-review/conflict as operational outcomes with
  ownership.
