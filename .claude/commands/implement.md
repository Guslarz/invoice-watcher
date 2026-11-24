---
description: Implement an approved plan following docs/agents/protocols/implement.md
argument-hint: PLAN.md
---

Follow `docs/agents/protocols/implement.md` exactly for the plan:

For a registered worker, require explicit plan approval in this worker session or a validated,
explicit developer-authorized handoff accepted by this bound replacement session under Protocol
parallel. Before editing read the exact approved plan/context and relevant code, state your
understanding and reconcile discrepancies. Reuse its
assigned worktree, and maintain lifecycle through `scripts/worker.py update`. Stop uncommitted
at the separate commit review gate; dispatch/resume never grants approval.
Address requirements, scope and approval questions directly to the developer here and pause;
only assignment/resource coordination belongs with the dispatcher.

Existing plans under `docs/agents/projects/<slug>.md` or `docs/agents/plans/<slug>.md`
remain valid inputs; continue using
the recorded path without moving another worker's file or resetting its approval gate.

$ARGUMENTS
