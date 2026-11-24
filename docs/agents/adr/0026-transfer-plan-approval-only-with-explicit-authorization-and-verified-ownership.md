---
id: ADR-0026
status: accepted
date: 2026-11-23
---

# ADR-0026: Transfer plan approval only with explicit authorization and verified ownership

## Context

Investigation during planning reveals uncertainty that initial dispatch cannot assess. A developer
may want a different implementation backend/model, but an implementation session must not infer
approval from its assignment or lose the evidence behind the plan. A completed assignment does
not prove that its editor stopped. The installed supervised Orca preamble also routes questions
to the coordinator, conflicting with developer discussion in each worker's visible conversation.

## Decision

Recommend implementation selection at plan review, defaulting to the current session and
respecting developer preferences. Use remaining uncertainty, rationale and escalation conditions;
do not rank models or automatically downgrade.

Keep developer requirements and approvals in the worker conversation. New Orca workers use
agent launch without the supervised preamble, preserving Chat/CLI preference and exact workspace
identity. Legacy supervised sessions retain their recorded continuation and retirement transport.
When the host CLI lacks developer-owned chat continuation or retirement, document manual chat
continuation and unsupported automatic retirement rather than claiming a prompt edit supplies an API.

A new implementer requires explicit developer selection and authorization to transfer observed
plan approval from the source conversation. Bind approval evidence to exact plan bytes, context,
revision and file state; preserve the original plan and resolved decisions outside tracked files.
Prove the exact source editor retired before launching another editor in the same worktree.
Persist ownership and recoverable checkpoints, bind the actual replacement, and require its
understanding of the packet and relevant code before enabling implementation. Ordinary resume
keeps the session; neither assignment completion nor lifecycle updates supply approval.

Commit approval remains a separate decision in the implementation conversation. Process checks
protect managed operations; they are not filesystem permission enforcement. Agent integrations
remain covered separately for execution backends and the Orca host under ADR-0014.

## Consequences

Implementation can use a different selected backend/model without discarding planning evidence
or manufacturing approval. Uncertain retirement, launch or file state blocks transfer. The
initial adapter explicitly excludes automatic native-backend and developer-owned chat source
retirement. Immutable transfer evidence survives normal deletion of the temporary plan.
