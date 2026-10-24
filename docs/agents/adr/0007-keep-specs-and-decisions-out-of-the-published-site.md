---
id: ADR-0007
status: accepted
date: 2026-10-24
---

# ADR-0007: Keep specs and decisions out of the published documentation site

## Context

ADR-0001 published specifications and decision records on the documentation site and built the
user pages from spec clauses through snippet includes. In practice the site's readers are users
of InvoiceWatcher. Normative clauses with IDs, gap markers and decision records are working
material for contributors and agents; quoted into user pages they read as a contract, not as an
explanation, and they tie the wording of user pages to the structure of the specs.

## Decision

- The published site serves users only: system overview, setup, results and the generated
  reference.
- Specifications live in `docs/agents/specs/` and decision records in `docs/agents/adr/`. Both
  stay in the repository and are excluded from the site, like the rest of `docs/agents/`.
- Published pages do not include, quote or link specs and ADRs. They explain behavior in their
  own words, written for the reader of the page.
- Specs remain the single normative source of behavior. A change to a spec updates the user
  pages that describe the changed behavior in the same change.

## Consequences

- User pages can be structured and worded for users, independent of clause numbering.
- Spec and user pages are kept consistent by process (same change, reviewed together), not by
  snippet includes; the build no longer catches a user page that drifted from its spec.
- The spec-first workflow of ADR-0001 still holds; only publication and snippet reuse change.
