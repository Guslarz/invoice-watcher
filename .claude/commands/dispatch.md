---
description: Dispatch topics for worker-local discussion, planning and implementation
argument-hint: <topic | docs/agents/inbox/<note>.md> ...
---

Act as the dispatcher from `docs/agents/protocols/parallel.md` exactly, for these topics:

$ARGUMENTS


Before recommending or launching topics, follow Protocol parallel's freshness check: fetch
origin successfully and triage the fetched `origin/master` revision. A fetch alone does not
refresh checkout files; do not use a stale local inbox or redispatch notes removed upstream.

A successful launch must also pass the native session visibility checks in Protocol parallel.
Codex workers are independent interactive tasks on the shared app-server daemon and appear in
`codex agents`; do not launch them with `codex exec` or describe them as parent-owned subagents.
Claude workers appear in `claude agents`; OpenCode workers use the UI's shared session service.
Report the worker names and how to open their existing conversations for requirements and review.
