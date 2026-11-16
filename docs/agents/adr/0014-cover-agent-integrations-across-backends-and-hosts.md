---
id: ADR-0014
status: accepted
date: 2026-11-11
---

# ADR-0014: Cover agent integrations across backends and hosts

## Context

The repository supports Claude Code, Codex CLI and OpenCode V2 as execution backends,
and Orca as a session host.
Shared instructions alone do not provide equivalent command discovery, permissions, worktree
hooks, or worker session APIs.
Previously the generator covered OpenCode commands and permissions, while Codex depended on
expanded worker prompts and manual protocol selection. Hook and argument-hint omissions were
recorded only in prose, and unknown hook events could pass the generator without review.

## Decision

Orca participates in the same compatibility table without becoming a worker execution backend.
Record Orca-owned support separately from behavior inherited from its hosted backend.

An execution backend runs the agent. A session host launches, displays and manages its
sessions. Compatibility targets declare one of these roles; Orca is classified as a host.

For every agent integration, record a disposition for all compatibility targets in
`config/agent-integrations.json`: **native**, **alternative**, or **unsupported**. An alternative
names the implemented adapter or explicit operational procedure. Unsupported entries explain
the repository limitation and any usable fallback. Do not imply that a repository limitation
is an upstream product limitation, or that process instructions enforce tool permissions.

Keep workflow prompts canonical in `.claude/commands/` and execution-backend permission inputs in
`.claude/settings.json`. Generate OpenCode V2 configuration, explicitly invoked Codex skills,
and the human-readable [compatibility matrix](../guidelines/agent-integrations.md) from those inputs
and the compatibility manifest. Keep lifecycle and approval requirements in
[Protocol parallel](../protocols/parallel.md). Use the shared worktree script when no native
hook adapter is installed. Do not translate Claude shell wildcards into broader Codex execution
prefixes.

Change integrations, their compatibility records, and generated adapters in the same change.
The generator rejects unknown command metadata/interpolation and settings, missing or obsolete
coverage, incomplete per-target dispositions, missing references, and generated output drift.
The manifest classifies targets explicitly and records per-target support. Its execution-backend
classifications must match the worker launcher's supported backends.

Review fingerprints protect hook definitions and the worker/worktree integration code from
unreviewed changes hidden behind unchanged feature names. Update fingerprints only after
reviewing affected integrations and their evidence; generation never approves or refreshes them.

Record official documentation sources and review dates. Revalidate affected adapters, commands,
flags, and API contracts when upgrading a backend or host, then update records and regression tests.
Check new agent integration code during review even outside the discovered configuration paths;
static inventory checks cannot discover every integration or detect upstream changes. Use
`scripts/verify.sh` for local verification; its documentation check and the Docs CI workflow
both run the compatibility drift check.

## Consequences

Workflow shortcuts have shared behavior and backend-appropriate discovery without copying
protocols. Unsupported integrations remain explicit and reviewable. Added configuration or
changed backend code requires compatibility review, including when generated output would be
unchanged. Tests exercise adapters with CLI/API mocks; live backend/host compatibility still needs
review against the recorded versions and official documentation. Backend releases are not
polled automatically, and review fingerprints attest a deliberate repository review rather
than guaranteeing correctness.
