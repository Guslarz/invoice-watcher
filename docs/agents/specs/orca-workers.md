---
id: SPEC-ORCA-WORKER
title: Orca-hosted repository workers
status: implemented
audience: [developer]
modules: [scripts]
---

# Orca-hosted repository workers

## Purpose

Developers dispatch visible Orca workers while retaining repository approval and cleanup rules.

## Behavior

- **ORCAWORKER-001** Worker launch distinguishes the execution backend from the session host.
  In an Orca session, automatic host selection uses Orca after verifying the caller and assigned
  local workspace. Outside Orca it preserves backend-native launch. Developers can select the
  host explicitly. An unavailable explicitly selected or detected Orca host fails without
  silently launching a worker outside Orca.
- **ORCAWORKER-002** Before starting a worker, dispatch protects its assigned repository
  worktree with lifecycle status. Launch uses that existing worktree, starts exactly one
  worker, records its host transport, workspace, actual surface and returned conversation or
  terminal identity, and verifies the intended workspace and backend. Supervised transports
  additionally retain their Run, Task and Dispatch identities. A visible external checkout
  alone is not successful worker launch.
- **ORCAWORKER-003** Orca-hosted launch follows the developer's Orca Chat/CLI preference and
  records the actual surface. It preserves requested model and agent choices when supported.
  Unsupported named agents, sandbox overrides, backend server selectors or model/effort
  combinations fail explicitly before launching; no requested option is silently dropped.
  Backend sandbox settings inherited through Orca are not reported as a confirmed repository
  sandbox override. Legacy backend-hosted workers retain their existing selection behavior.
- **ORCAWORKER-004** Repository lifecycle phase, not host assignment completion or process exit,
  determines cleanup eligibility. Developer-owned conversations pause for requirements and
  approvals directly with the developer and retain the protected repository phase. Legacy
  supervised workers also report their assignment outcome at a pause without granting approval
  or marking repository work done. Completion reports never relay requirements or approval
  decisions through the dispatcher.
- **ORCAWORKER-005** Resume protects repository status before attaching or delivering input.
  It targets the recorded Orca worker and preserves its backend conversation, worktree and
  pending approval gates. Developer-owned terminal conversations use the recorded terminal's
  supported continuation route; developer-owned chats use their existing visible chat through
  the user interface when no CLI continuation API exists. Legacy supervised conversations use
  the recorded continuation API. A manual route is reported as manual, not as delivered input
  or an attached session. Resume never substitutes a new worker for an unavailable session.
  A separately authorized implementation handoff uses the explicit transfer operation rather
  than resume, proves predecessor retirement, preserves the assigned worktree and binds the
  replacement identity before enabling implementation.
- **ORCAWORKER-006** Launch and continuation retain replay identities supplied by Orca for the
  selected transport. Ambiguous or failed operations preserve protected status and all known
  identities for inspection and recovery. Replay uses only that operation's supported identity
  and endpoint. Silence, a timeout or an unknown outcome never authorizes a fresh launch,
  automatic reassignment or deletion of an unproven resource.
- **ORCAWORKER-007** Prune applies existing lifecycle, PR reconciliation and Git safety checks
  before contacting Orca for cleanup. It releases only the exact recorded resources through
  their supported transport, confirms retirement, and removes the workspace without force,
  archive hooks or branch deletion. Developer-owned resources never use a fabricated
  supervised assignment. When the host lacks an exact retirement API for a chat, automatic
  retirement and workspace removal are unsupported and preserve protection with an actionable
  manual diagnostic. User-owned, unrelated, retained or unverifiable resources are not forcibly
  closed. Branch and registry retirement follow confirmed safe workspace removal only.
- **ORCAWORKER-008** Cleanup checkpoints make retries safe after resource release, workspace
  removal or branch retirement. Explicit absence of an exact recorded resource is idempotent;
  generic errors and lost replies are not absence or proof of predecessor exit. Successful
  cleanup leaves neither the worker's active surface nor its workspace entry. Historical
  coordination records may remain; cleanup never resets a Run or erases unrelated history.

- **ORCAWORKER-009** New repository workers use a developer-owned Orca conversation adapter
  that launches into the assigned workspace without a supervised coordinator-only question
  preamble. Launch preserves the developer's Chat/CLI preference, records the actual surface,
  and verifies prompt acceptance and returned identity. Requirements and approvals remain in
  that conversation. Existing supervised workers remain identifiable and resumable through
  their original transport; they are not silently migrated. Unsupported surface operations
  report their manual alternative or limitation rather than launching another conversation.

## Acceptance

- **ORCAWORKER-001** Given Orca and non-Orca callers, When dispatch selects its host, Then it
  uses the verified host or returns a diagnostic, including missing CLI/runtime and ambiguous
  workspace cases; explicit backend selection still works outside Orca transport.
- **ORCAWORKER-002** Given a shared-script worktree, When a worker starts, Then status already
  protects it before the first external launch call, the Orca worker is visible in that exact
  workspace, and returned identities are persisted before success is reported.
- **ORCAWORKER-003** Given each supported backend and requested launch options, When dispatch
  starts it, Then supported choices reach Orca unchanged and unsupported choices start nothing.
  Chat/CLI preference and actual surface are reported without claiming unsupported parity.
- **ORCAWORKER-004** Given a worker awaiting plan or commit approval, When it reports completion
  of its current Orca assignment, Then its repository approval phase remains protected and
  prune does not treat the settled assignment as finished repository work.
- **ORCAWORKER-005** Given working, kept-open and closed-resumable workers, When resumed, Then
  the recorded conversation continues in Orca without a second independent session. Failed
  attachment leaves the worktree protected and approval gates intact.
- **ORCAWORKER-006** Given a launch or continuation with a lost reply, When recovery runs,
  Then only the accepted operation's replay identity is reused; no fresh worker is started.
- **ORCAWORKER-007** Given cleanup-eligible work, When prune runs, Then exact resource release
  precedes non-forced Orca workspace removal and branch retirement. Dirty/current/master
  worktrees, failed PR lookup, active approval gates, unrelated tabs and unconfirmed release
  remain protected. Orca-hosted cleanup does not also call backend-native session deletion.
- **ORCAWORKER-008** Given injected failures after each cleanup checkpoint, When cleanup is
  retried, Then it completes safely or reports the remaining blocker without losing identity,
  deleting another session or treating an arbitrary failure as resource absence.

- **ORCAWORKER-002/004/009** Given each backend and Chat/CLI preference, When new repository
  workers launch, Then the adapter uses agent launch without orchestration preamble injection,
  binds the returned workspace/surface/identity, and accepts the workflow prompt exactly once;
  developer questions stay local, and legacy supervised identities are preserved unchanged.
- **ORCAWORKER-005/006** Given developer-owned chat and terminal conversations or a lost launch
  reply, When resuming or recovering, Then terminal input uses its recorded handle, chat resume
  reports the existing UI conversation, and replay uses the recorded launch operation ID with
  retry-launch rather than the supervised mutation API or a fresh launch.
- **ORCAWORKER-007/008** Given supervised, terminal and chat resources, When cleanup runs, Then
  only the correct transport operates, exact retirement proof precedes workspace removal, and
  unsupported chat retirement leaves the workspace protected with a manual diagnostic.


## Configuration

The worker launcher selects the session host independently of the execution backend.

## Out of scope

Remote and paired-host dispatch; migration of existing backend-native sessions.
