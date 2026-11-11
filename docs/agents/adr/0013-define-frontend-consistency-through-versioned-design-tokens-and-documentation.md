---
id: ADR-0013
status: accepted
date: 2026-11-11
---

# ADR-0013: Define frontend consistency through versioned design tokens and documentation before shared components

## Context

The project has no frontend today. Future user-facing applications, starting with registration
(see `docs/agents/inbox/user-registration-app.md`), will need a consistent visual language without
prematurely committing the repository to a frontend framework, component library or build tool.

Several sources of consistency are possible: shared design tokens, a shared component library,
documentation-led design guidance, and design-tool-generated assets. The choice affects where
design decisions live, how applications consume them, how changes are reviewed, and when
automation becomes worthwhile.

## Decision

- **Select versioned design tokens and documentation as the initial source of truth.** Define
  foundational decisions — color, typography, spacing, layout, elevation, motion, iconography and
  accessibility constraints — as named tokens and written patterns in the repository. Treat the
  token set and the frontend guideline as the canonical reference until a real frontend exists.
- **Keep design-tool files as inputs, not the committed source of truth, until the first
  frontend is planned.** If a design tool is used, exported artifacts or token values must be
  translated into repository-owned, reviewable files; the repository must not depend on access to a
  proprietary tool to understand the current design direction.
- **Defer a shared component library, frontend framework, package layout and automated visual
  checks.** Do not create frontend packages, components or consistency automation in this change.
  Revisit these choices when the first frontend application defines its runtime, build and
  accessibility requirements, using the tokens and guideline as the contract to implement.
- **Govern changes through repository review.** Any change to tokens or design guidance is made in
  a pull request that explains the user-facing intent, accessibility impact and affected
  applications or future consumers. Breaking visual changes require an explicit migration note in the
  same change.
- **Require staged adoption.** The first frontend consumes tokens and documented patterns directly;
  shared packages or generated artifacts are introduced only when at least two consumers need the
  same implementation and the ownership boundary is clear.

## Alternatives considered

- **Shared component library first:** would maximize reuse but forces framework, styling, testing
  and release decisions before any frontend exists, and risks a library disconnected from real
  product needs.
- **Documentation-led design only:** would be lightweight, but prose without named tokens makes
  reuse, review and later automation ambiguous.
- **Design-tool-generated assets as the source of truth:** can speed exploration, but makes the
  canonical design depend on tool access, export fidelity and generated-file churn; suitable later
  as an authoring input when export to repository-owned tokens is defined.

## Consequences

- **Easier:** future frontend work starts from explicit, reviewable decisions; early applications can
  stay lightweight while still sharing vocabulary and accessibility expectations.
- **Easier:** framework and implementation choices can be made with real requirements from the first
  frontend instead of speculation.
- **Harder:** consistency relies on discipline and review until automated checks exist; token names
  and patterns must be kept coherent as the first applications adopt them.
- **Must keep doing:** update the frontend guideline and token definitions together; record deferred
  implementation choices in the first frontend plan; do not introduce frontend code or shared
  packages without a plan that names their consumers and verification.
