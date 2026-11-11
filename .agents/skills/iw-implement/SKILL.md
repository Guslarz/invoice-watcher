---
name: iw-implement
description: "Implement an approved plan following docs/agents/protocols/implement.md"
---

<!-- Generated from .claude/commands/implement.md; do not edit by hand. -->

Resolve repository paths from the worktree root. Read `AGENTS.md` before following the workflow.

Inputs: `docs/agents/projects/<slug>.md`. Use the inputs supplied with `$iw-implement`; ask for missing required inputs before dependent work.

Follow `docs/agents/protocols/implement.md` exactly for the plan:

For a registered worker, require explicit plan approval in this worker session, reuse its
assigned worktree, and maintain lifecycle through `scripts/worker.py update`. Stop uncommitted
at the separate commit review gate; dispatch/resume never grants approval.

the inputs supplied alongside this skill invocation
