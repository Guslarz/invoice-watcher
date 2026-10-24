---
id: ADR-0001
status: superseded by ADR-0007
date: 2026-10-13
---

# ADR-0001: Describe behavior in published specs and build user docs from them

## Context

The project is developed mostly with AI agents. Agents need an unambiguous description of what
the system must do, and users need documentation. Writing both separately makes them drift apart.

## Decision

- Each capability has a specification (originally in docs/specs/, now `docs/agents/specs/`) with numbered, normative clauses and
  Given/When/Then acceptance criteria. It is the source of truth for behavior.
- Changes that alter behavior are planned first (`docs/agents/protocols/plan.md`): the spec
  changes before the code, and tests name the clauses they verify.
- Specifications are published as-is on the documentation site (MkDocs, GitHub Pages). Guides
  quote spec clauses through snippets instead of restating them; reference pages are generated
  from code.
- Agent-only material (`docs/agents/`) lives next to it but is not published.

## Consequences

- Behavior changes cost one extra step (spec + plan) but every published statement has one owner.
- Specs must be written for humans too: plain language, no implementation details.
- The site build (`mkdocs build --strict`) and `scripts/check-docs.sh` guard links and structure.
  The site is built with Zensical since [ADR-0008](0008-build-the-documentation-site-with-zensical.md).
