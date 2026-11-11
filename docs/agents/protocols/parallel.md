# Protocol parallel

Use for independent topics that proceed in separate workers. Related topics that touch the same
files are grouped on one branch. The dispatcher never implements or drafts workers' plans.

Backend integration support and operational alternatives are recorded in the
[compatibility matrix](../guidelines/agent-backends.md), maintained under ADR-0014.

## Dispatch and approval

- **WORKER-001** The dispatcher triages and groups overlapping topics, creates the assigned
  worktree, and starts its worker. Requirements discussion, plan creation, documentation checks,
  and plan approval occur in that worker. Dispatch does not approve implementation or a commit.
  A planning worker uses its assigned worktree without pruning or creating another worktree.
- **WORKER-002** Workers support Claude Code, OpenCode V2, and Codex CLI. Backend, model, and
  agent selection are separate. An explicit worker agent takes precedence; otherwise the
  dispatcher inherits its current named agent when reliably known and passes it explicitly
  as `--agent <name>`. If no named agent identity is available, use `--default-agent` without
  asking. Do not replace a known named agent with the backend default.
  Missing, unsupported, or unavailable named agents never silently fall back. Model names do
  not identify agents. Backend-specific configuration profiles are not implicitly agent names.
- **WORKER-005** The launcher accepts `/plan`, `/implement`, and `/lightweight`. A plan is
  written from the repository template and checked before its approval gate. Only explicit
  developer plan approval in the worker permits implementation. Workers write `.gitmessage`
  and stop uncommitted at commit review. Only explicit commit approval in that worker permits
  commit, push, and PR creation. PR creation sets `awaiting-merge` and records PR identity.
- **WORKER-006** Starting and resuming a worker protects its worktree before backend execution
  or interactive attachment. Resume preserves pending approval gates, or moves an otherwise
  cleanup-eligible worker to `discussing` before opening its session. Worker instructions require
  phase updates before further work, including when resumed through native backend tools.
  Resume reuses the same session and worktree; it grants no approval by itself.
- **WORKER-012** Codex workers use `danger-full-access` by default. `start --sandbox <mode>`
  selects `read-only`, `workspace-write`, or `danger-full-access` explicitly. The launcher
  records the effective mode in the registry and lifecycle status, displays it in status
  reports, and passes it to Codex on start and resume. `resume --sandbox <mode>` changes the
  recorded selection before attachment when the server applies it. A loaded native task that
  ignores an override is reported explicitly and retains its previous recorded mode.
  Legacy Codex records without a selection use the launcher default. Sandbox options are rejected for other backends. Sandbox selection does
  not grant plan or commit approval and does not bypass approval handling.
- **WORKER-013** A dispatched worker is discoverable by its recorded identity and readable name
  in the backend's native session interface. Codex uses native interactive tasks or native
  subagents, Claude uses named background sessions listed by agent view, and OpenCode uses
  sessions on the same shared service as its UI. Dispatch does not silently substitute a
  headless session when native dispatch is unavailable. Visibility verification is part of
  launch validation and is independent of lifecycle completion.
- **WORKER-014** Native dispatch protects the assigned worktree before starting a turn and
  records the returned session identity before further execution. Resume and follow-up reuse
  that session and worktree, preserve pending approval gates, and provide a backend-native
  way to reopen the worker. Discovery failures preserve protected status and actionable
  diagnostics. Native task visibility does not imply a parent/subagent relationship.

`danger-full-access` removes filesystem and network sandbox enforcement. Codex approval
handling remains separate. Use `--sandbox workspace-write` where host namespace support is
available and sandboxed execution is desired.

1. For inbox batches use `python3 scripts/inbox.py ready`. Run `scripts/worktree.sh prune` and
   report kept worktrees. Group overlapping topics; never run them in parallel.
2. Determine planning versus lightweight and select backend/agent as above. Use the developer's
   backend, otherwise the dispatcher's; ask if unknown. Do not infer an agent from a model.
3. Create each assigned worktree with `scripts/worktree.sh new <type>/<slug>` and launch:

   ```sh
   python3 scripts/worker.py start --backend "$BACKEND" --agent "$AGENT" --name example --worktree /absolute/path/to/worktree --command '/plan description or inbox path'
   ```

   Pass the requested agent, or otherwise the dispatcher's current named agent, with `--agent`.
   Use `--default-agent` without confirmation only when neither named identity is available.
   Optional `--model` is independent: OpenCode accepts `provider/model#variant`,
   Claude/Codex accept their own model names. Codex currently supports default-agent operation,
   not named-agent selection; a Codex configuration profile is not an agent.
   Codex uses the existing local shared app-server daemon, with `danger-full-access` by default.
   Start `codex agents` first if the daemon is unavailable; `--codex-socket` selects another local
   control socket. The launcher never starts a private server or falls back to `codex exec`.
   When `CODEX_THREAD_ID` is available, it forks that task to inherit its configuration and
   completed context; `--parent-session` explicitly selects another source. The worker is an
   independent interactive task, not a spawned child subagent. Omit `--model` to inherit the source model.
   Native UI creation tools that cannot assign the required worktree are not a dispatch substitute.
   Remote WebSocket servers are unsupported by this local-socket adapter; run dispatch on the
   UI server's host with its control socket instead of silently using a different local daemon.
   OpenCode defaults to its shared service; `--server <URL>` selects the same service as a UI
   using an explicit server. The URL is retained for listing, submission, resume, and cleanup.
   Neither adapter uses a private/standalone server. For lightweight work use `/lightweight`;
   `/implement` requires an already approved plan.
4. Give the developer the worker name and `python3 scripts/worker.py resume <name>` command.
   Requirements questions and approvals belong in that worker, not the dispatcher. A failed
   launch remains protected; inspect its diagnostics rather than starting another worker.
   Open Codex workers in `codex agents`, Claude workers in `claude agents`, and OpenCode workers
   in its session picker on the same service/project. A directory-filtered OpenCode view may
   require selecting the assigned worktree. `worker.py resume` also reopens the recorded session.
   Codex launch checks the default interactive inventory. If native task-list tools are available
   in the dispatcher, also confirm the returned session in that UI's list before reporting it;
   a mismatch means the UI and launcher are connected to different servers and must be reported.
   Existing legacy Codex exec workers stay resumable by their recorded identity; they are not
   migrated or made visible automatically. Native task creation does not imply subagent parentage.
5. Keep at most four workers running verification concurrently (dispatcher responsibility).
   Use `python3 scripts/worker.py list`, `status <name>`, and `scripts/worktree.sh list` to inspect
   progress. Status reports workflow phases, not whether a model is generating.

## Lifecycle contract

- **WORKER-003** A gitignored `.worker-status.json` in the worktree is the sole authority for
  worker lifecycle. It contains a schema version, worker/worktree/branch identity, backend,
  session identity when known, selected agent/default policy, optional model, lifecycle phase,
  timestamp, optional plan path, optional PR identity, and optional explanatory detail.
  Script-managed identity fields cannot be overwritten through a lifecycle update.
  Updates are validated, serialized, and atomic. Missing files mean legacy lifecycle `done`;
  malformed, unsupported-version, or identity-mismatched files block cleanup with a reason.
- **WORKER-004** Lifecycle phases are `discussing`, `planning`, `awaiting-plan-approval`,
  `implementing`, `awaiting-commit-review`, `awaiting-merge`, `done`, and `blocked`.
  All phases except `done` and confirmed-merged `awaiting-merge` prevent automatic cleanup.
  Timestamps are informational, not expiry timers. A completed model turn or process exit is
  not completion of the work. A launch failure preserves a protected status and its diagnostics.
- **WORKER-011** Dispatcher status shows lifecycle, backend, agent/default policy, model,
  worktree, plan/PR when present, and an actionable reason or resume command. It distinguishes
  missing legacy status from malformed status and exposes incomplete launches/cleanup without
  claiming backend execution state. Registry and status identity conflicts are reported rather
  than silently assigned to a different session.

Workers use `python3 scripts/worker.py update <name> --phase <phase>` before changing activities;
add `--plan <relative-path>` at plan approval, `--detail <text>` for a blocker, and
`--pr <https://github.com/owner/repo/pull/number>` at `awaiting-merge`. Do not hand-edit JSON.
The registry in the common Git directory indexes names/session identities and serializes
operations; it is not a second lifecycle authority. An update never grants developer approval.

Native backend resume must protect status before any work. Managed resume does this automatically.
The deliberate tradeoff is that a deleted file or stale `done` is trusted: unmanaged sessions
that ignore this contract can be pruned. There is no background-session polling or age timeout.
Out-of-scope findings go to the inbox. Before an approved push, fetch/rebase on origin/master and
verify again; regenerate generated reference pages instead of resolving them by hand.

## Cleanup

- **WORKER-007** Prune and status commands do not query backend session activity. Prune derives
  eligibility only from the status file (or its absence), plus PR reconciliation and Git safety.
  Old registry entries, unavailable session-status APIs, and unregistered legacy worktrees do
  not independently prevent cleanup. Backends are contacted for session deletion, not lifecycle
  discovery. Short-lived script locks serialize updates, resume handoff, and cleanup; an
  attached-session lock is not a second lifecycle authority.
- **WORKER-008** For `awaiting-merge`, prune checks the recorded repository and PR number/URL
  with `gh`. A confirmed matching merged PR makes the worktree cleanup-eligible even if the
  worker never updates its file. Open or closed-unmerged PRs remain protected. Missing PR
  identity, lookup failure, or mismatched repository/branch leaves it protected with a reason.
- **WORKER-009** Cleanup never removes `master`, the current worktree, a detached HEAD, dirty
  work, or commits neither pushed nor merged. A fresh successful fetch is required. A merged
  PR may establish merge safety for a squash/rebase merge only when its recorded head commit
  matches the worktree's current HEAD; later local commits are not covered by the PR merge.
  The existing threshold of ten non-master worktrees and `prune --all` override are unchanged.
- **WORKER-010** Cleanup deletes only the session explicitly associated with the eligible
  worktree, using its recorded backend. A missing status file may use a matching legacy
  registry entry for deletion identity, never for lifecycle eligibility. With no recorded
  session, cleanup removes the safe worktree without searching backends for sessions.
  Confirmed session absence is idempotent success. Other deletion failures retain the worktree
  and cleanup identity for retry, reporting incomplete cleanup. Branch deletion follows
  successful worktree removal. Metadata is retired only after successful cleanup.

`scripts/worktree.sh prune` applies the threshold; `prune --all` checks at any count. Prune reports
each keep reason. Malformed status, failed PR lookup, failed fetch, and failed session deletion
are not permission to guess. `python3 scripts/worker.py remove <name>` deletes an eligible
worker's session and registry entry but retains its worktree; `scripts/worktree.sh rm <slug>`
uses the same lifecycle and Git checks to remove a worktree and any recorded session.

Previously, unregistered worktrees and unknown backend states were intentionally retained.
Missing-file-as-done replaces those safeguards for legacy cleanup. A legacy worktree without
session metadata can be removed, but its unrecorded sessions cannot be identified for deletion.
Legacy Claude records may have a conversation UUID but lack the short background job ID needed
for attach/delete. Supply an independently known ID with
`python3 scripts/worker.py record-session <name> --backend-session <job-id>` (for example from
the original launch log). Do not infer it from the UUID or query session activity for cleanup.
Claude's `rm` removes the background job; its saved conversation transcript remains resumable,
as documented by Claude Code. No broad transcript purge is performed.
