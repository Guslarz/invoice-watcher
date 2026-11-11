---
name: iw-plan
description: "Plan a change following docs/agents/protocols/plan.md"
---

<!-- Generated from .claude/commands/plan.md; do not edit by hand. -->

Resolve repository paths from the worktree root. Read `AGENTS.md` before following the workflow.

Inputs: `<change description | docs/agents/inbox/<note>.md>`. Use the inputs supplied with `$iw-plan`; ask for missing required inputs before dependent work.

Follow `docs/agents/protocols/plan.md` exactly for this change:

If this is a registered worker, reuse its assigned branch/worktree, maintain its lifecycle
through `scripts/worker.py update`, and discuss requirements and obtain approval here.
Dispatch is not plan or commit approval.

the inputs supplied alongside this skill invocation
