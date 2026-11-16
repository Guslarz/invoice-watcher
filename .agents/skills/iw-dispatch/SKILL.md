---
name: iw-dispatch
description: "Dispatch topics for worker-local discussion, planning and implementation"
---

<!-- Generated from .claude/commands/dispatch.md; do not edit by hand. -->

Resolve repository paths from the worktree root. Read `AGENTS.md` before following the workflow.

Inputs: `<topic | docs/agents/inbox/<note>.md> ...`. Use the inputs supplied with `$iw-dispatch`; ask for missing required inputs before dependent work.

Act as the dispatcher from `docs/agents/protocols/parallel.md` exactly, for these topics:

the inputs supplied alongside this skill invocation


Before recommending or launching topics, follow Protocol parallel's freshness check: fetch
origin successfully and triage the fetched `origin/master` revision. A fetch alone does not
refresh checkout files; do not use a stale local inbox or redispatch notes removed upstream.

Use the session-host selection in Protocol parallel. In Orca, `worker.py start --host auto`
launches a visible Orca worker in the shared-script worktree, following Chat/CLI preference.
Keep repository lifecycle status authoritative across Orca assignment completion and approval
pauses; resume and cleanup must use the recorded Orca identities. Do not launch a second
backend-native worker as a fallback. Require an exact workspace selector for ambiguous checkouts.

For backend-hosted workers, a successful launch must also pass the native session visibility checks in Protocol parallel.
Codex workers are independent interactive tasks on the shared app-server daemon and appear in
`codex agents`; do not launch them with `codex exec` or describe them as parent-owned subagents.
Claude workers appear in `claude agents`; OpenCode workers use the UI's shared session service.
Report the worker names and how to open their existing conversations for requirements and review.
