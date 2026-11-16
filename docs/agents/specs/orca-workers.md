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
  worktree with lifecycle status. Launch uses that existing worktree, starts exactly one worker,
  records its Orca Run, Task, Dispatch, workspace and session identities when available, and
  verifies the returned worker belongs to the intended workspace and execution backend.
  A visible external checkout alone is not successful worker launch.
- **ORCAWORKER-003** Orca-hosted launch follows the developer's Orca Chat/CLI preference and
  records the actual surface. It preserves requested model and agent choices when supported.
  Unsupported named agents, sandbox overrides, backend server selectors or model/effort
  combinations fail explicitly before launching; no requested option is silently dropped.
  Backend sandbox settings inherited through Orca are not reported as a confirmed repository
  sandbox override. Legacy backend-hosted workers retain their existing selection behavior.
- **ORCAWORKER-004** Repository lifecycle phase, not Orca Task completion or process exit,
  determines cleanup eligibility. Workers report each Orca assignment's outcome when pausing
  for approval while retaining their repository approval phase. Settling an Orca assignment
  grants no implementation or commit approval and does not mark repository work done.
- **ORCAWORKER-005** Resume protects repository status before attaching or delivering input.
  It targets the recorded Orca worker and preserves its backend conversation, worktree and
  pending approval gates. Closed but resumable workers reopen through Orca's supported
  continuation API. It never substitutes a new independent worker for an unavailable session.
- **ORCAWORKER-006** Launch and continuation retain replay identities supplied by Orca.
  Ambiguous or failed operations preserve protected status and all known identities for
  inspection/recovery. Silence, a timeout or an unknown outcome never authorizes a fresh
  launch, automatic reassignment or deletion of an unproven resource.
- **ORCAWORKER-007** Prune applies existing lifecycle, PR reconciliation and Git safety checks
  before contacting Orca for cleanup. It releases only the exact recorded worker resources,
  confirms release, and removes the workspace through Orca without force, archive hooks or
  branch deletion. The repository rechecks safety and retires the branch and registry only
  after workspace removal succeeds. User-owned, unrelated, retained, unsettled or unverifiable
  resources block cleanup with a reason; they are not forcibly closed.
- **ORCAWORKER-008** Cleanup checkpoints make retries safe after partial release, workspace
  removal or branch retirement. Explicit absence of the exact recorded resource is idempotent;
  generic errors and lost replies are not absence. Successful cleanup leaves neither the
  worker's active Orca surface nor its workspace entry. Historical Orca coordination records
  may remain; pruning never resets a Run or erases unrelated history.


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


## Configuration

The worker launcher selects the session host independently of the execution backend.

## Out of scope

Remote and paired-host dispatch; migration of existing backend-native sessions.
