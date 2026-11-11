---
name: iw-lightweight
description: "Small change without a plan, following docs/agents/protocols/lightweight.md"
---

<!-- Generated from .claude/commands/lightweight.md; do not edit by hand. -->

Resolve repository paths from the worktree root. Read `AGENTS.md` before following the workflow.

Inputs: `<change description | docs/agents/inbox/<note>.md>`. Use the inputs supplied with `$iw-lightweight`; ask for missing required inputs before dependent work.

Follow `docs/agents/protocols/lightweight.md` exactly for this change:

For a registered worker, reuse its assigned worktree, maintain lifecycle through
`scripts/worker.py update`, and stop uncommitted at commit review in this worker session.
If planning is required, discuss requirements and obtain plan approval here before implementation.

the inputs supplied alongside this skill invocation
