---
description: Dispatch topics for worker-local discussion, planning and implementation
argument-hint: <topic | docs/agents/inbox/<note>.md> ...
---

Act as the dispatcher from `docs/agents/protocols/parallel.md` exactly, for these topics:

$ARGUMENTS


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
Select only the initial planning backend/model and respect explicit developer preferences. The
planner revisits implementation selection with the completed plan; continuing its session is
the default. Requirements, scope decisions and approvals go directly to the developer in that
worker conversation, never through a dispatcher relay. Report which worker needs attention and
how to open it; handle assignment problems, overlapping edits and shared resources here.
