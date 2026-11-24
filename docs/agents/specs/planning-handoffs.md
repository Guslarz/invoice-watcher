---
id: SPEC-HANDOFF
title: Planning and implementation handoffs
status: implemented
audience: [developer]
modules: [scripts]
---

# Planning and implementation handoffs

## Purpose

Developers choose the implementation session after investigation and transfer approved work
without losing context, ownership or separate approval gates.

## Behavior

- **HANDOFF-001** Dispatch selects the initial planning backend/model and preserves explicit
  developer preferences. At plan review the planner recommends continuing its current
  session, or names a specific available implementation backend/model, explains the remaining
  uncertainty and rationale, and states conditions requiring renewed investigation or developer
  escalation. Continuing the current session is the default. No automatic downgrade or fixed
  model ranking determines the recommendation.
- **HANDOFF-002** Requirements, scope decisions, plan approval and commit approval are addressed
  directly to the developer in the existing visible worker conversation, where the worker
  pauses for the answer. Coordination with the dispatcher is limited to assignment problems,
  cross-worker conflicts, shared resources and completion reports. The dispatcher identifies
  the worker needing attention and how to open it, without relaying requirements or approvals.
- **HANDOFF-003** Plan approval and implementation-session selection are distinct decisions.
  Dispatch, resume, a recommendation, lifecycle updates and assignment completion grant neither
  plan nor commit approval. Replacement requires explicit developer selection and explicit
  authorization to transfer the approval recorded in the planning conversation to that target.
  Approval in the planning conversation remains valid in a replacement only through this
  recorded transfer; otherwise the implementation conversation requests approval itself.
- **HANDOFF-004** A replacement receives the same worktree and branch, the exact approved plan
  bytes, resolved decisions and constraints, relevant specifications and decisions, investigation
  evidence, remaining uncertainty, escalation conditions and verification requirements. Its
  transfer record binds the source identity, target selection, plan digest, repository revision,
  relevant uncommitted file state and the developer's approval evidence. Missing, changed or
  conflicting inputs block implementation until reconciled with the developer.
- **HANDOFF-005** Before replacement starts, the launcher protects lifecycle status and proves
  the exact preceding editor has stopped through its supported host adapter. A settled
  assignment, silence, timeout, fencing alone or a short script lock does not establish that
  proof. Replacement binds its actual session identity before implementation is enabled and
  rejects stale ownership. No second editor starts while retirement is unconfirmed.
- **HANDOFF-006** The replacement reads the transferred material and relevant implementation
  code, confirms its understanding and reports material discrepancies before editing. The
  launcher accepts acknowledgment only from the bound replacement for the unchanged transfer.
  Commit approval is separately obtained in that implementation conversation.
- **HANDOFF-007** Transfer checkpoints preserve lifecycle protection, approval evidence and all
  allocated identities across failure. Recovery reuses recorded mutation identities and never
  launches a second replacement to resolve an unknown outcome. Status distinguishes the
  active owner, pending transfer and next action. A retired predecessor is never resumed as
  the current worker without an explicit reconciled ownership transfer.
- **HANDOFF-008** Support is recorded separately for each backend and session host. Unsupported
  replacement, model selection or developer-question routing fails explicitly with the
  supported alternative; requested settings are never dropped. Ordinary resume continues the
  recorded conversation and does not switch backend/model or transfer approval.

## Acceptance

- **HANDOFF-001** Given explicit model preferences and plans with differing uncertainty, When
  planning finishes, Then the review contains the concrete recommendation, rationale and
  escalation conditions without model rankings, automatic changes or inferred approval.
- **HANDOFF-002** Given a requirements question or approval pause, When a worker stops, Then
  its developer-facing question stays in that conversation and the coordinator receives only
  the attention location; a shared-resource conflict still uses coordination mail.
- **HANDOFF-003** Given absent, ambiguous or source-only approval, When replacement is requested,
  Then implementation cannot begin without approval plus explicit transfer to the named target;
  no resulting record contains commit approval or implies that a dispatch grants approval.
- **HANDOFF-004** Given an approved packet and dirty worktree, When plan bytes, scoped input
  files, branch, revision, decisions or target differ, Then acceptance fails with the mismatch;
  unchanged inputs deliver the original plan and complete context to the replacement.
- **HANDOFF-005** Given confirmed stopped, unknown, retained, user-owned and still-active
  predecessors, When handoff runs concurrently with resume/update/prune, Then only a proven
  retired exact predecessor permits one replacement, and stale writers cannot update ownership.
- **HANDOFF-006** Given a bound new session, When it has not yet acknowledged the packet and
  relevant code, Then it cannot enter implementing through the managed workflow; acknowledgment
  of unchanged approved context enables implementation, while discrepancies require review.
- **HANDOFF-007** Given failure after every retirement/allocation/binding/delivery checkpoint,
  When recovery or an ordinary resume is attempted, Then status remains protected, identities
  and approval survive, and ambiguous operations cause no duplicate launch or old-owner resume.
- **HANDOFF-008** Given each backend/host and unsupported settings, When a developer requests
  resume or handoff, Then supported choices are preserved and limitations produce diagnostics
  and documented alternatives rather than fallback.
## Configuration

Repository workflow commands and the worker launcher manage handoff packets locally.

## Out of scope

- Automatic model ranking, permission translation, native-backend source retirement and
  automatic retirement of developer-owned chat sources.
