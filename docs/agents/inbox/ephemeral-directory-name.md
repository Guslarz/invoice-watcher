---
title: Fix the misspelled ephemereal environment directory
kind: tech-debt
area: deployment
added: 2026-10-24
---

`deployments/infrastructure/environments/dev/ephemereal` should be `ephemeral`. The name is used in:
- the directory itself and its GCS state prefix `infrastructure/dev/ephemereal`
  (`ephemereal/main.tf:8`) — renaming needs a state migration (`tofu init -migrate-state` or a
  copy of the state object), not only a move,
- `.github/workflows/environment.yml:31` (`module: ephemereal`, used by `infrastructure.yml:36`),
- `docs/agents/guidelines/deployments.md:7`, `docs/setup/environment.md`, `docs/setup/one-time.md`,
  `docs/setup/index.md`, `docs/agents/inbox/installation-values.md`.

`scripts/check-deployments.sh` uses a glob and needs no change.
