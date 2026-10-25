---
title: Research how to keep several frontend apps visually consistent
kind: idea
area: docs
added: 2026-10-25
---

The project will get several frontend apps (first: user registration, `user-registration-app.md`).
Before building the first one, research how to write and maintain one design reference they all
follow: e.g. design tokens (colors, spacing, typography) as a shared package, a shared component
library, a documented design system page in `docs/`, or a design tool as the source of truth with
generated tokens. Decide where it lives in the repository (a `libs/`-like area for frontend code?),
how apps consume it, and how consistency is checked (lint rules, visual tests). Outcome: an ADR and
a guideline in `docs/agents/guidelines/`.
