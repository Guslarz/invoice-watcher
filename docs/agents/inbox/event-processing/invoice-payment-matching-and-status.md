---
title: Build invoice-payment matching and status projection
kind: idea
area: events
added: 2026-11-11
status: later
size: L
blocked-by: [event-processing/invoice-recognition-pipeline, event-processing/payment-recognition-pipeline]
---

Stage 3 in the [initiative roadmap](README.md) (ADR-0011, ADR-0012). Implement stateful
matching and a derived invoice payment-status projection. Support idempotent, replay-safe handling of
duplicates and out-of-order inputs; partial/multiple allocations; unmatched/conflicting/ambiguous
review states; and corrections/reversals as new facts. Trusted bank e-mails reporting executed/booked
transfers may automatically resolve invoices as paid or partially paid after sufficient matching on
amount, currency, recipient and invoice/reference information. Do not require a bank API or manual
confirmation for every transfer. Exclude generic claims, unverified sources and non-executed transfers
from paid totals; count each transfer once across notifications. Specify source-trust and matching
rules, currency/FX/tolerance handling and manual resolution for ambiguity. Reversals must remove
prior payment effects and reopen balances. Every projected status must be explainable by stored
evidence and match decisions.
