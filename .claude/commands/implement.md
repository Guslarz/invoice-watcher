---
description: Implement an approved plan following docs/agents/protocols/implement.md
argument-hint: PLAN.md
---

Follow `docs/agents/protocols/implement.md` exactly for the plan:

For a registered worker, require explicit plan approval in this worker session, reuse its
assigned worktree, and maintain lifecycle through `scripts/worker.py update`. Stop uncommitted
at the separate commit review gate; dispatch/resume never grants approval.

Existing plans under `docs/agents/projects/<slug>.md` or `docs/agents/plans/<slug>.md`
remain valid inputs; continue using
the recorded path without moving another worker's file or resetting its approval gate.

$ARGUMENTS
