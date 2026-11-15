# Agent backend compatibility

<!-- Generated from config/agent-backends.json; do not edit by hand. -->

Support describes this repository integration, not every upstream product capability.
**native** uses a backend mechanism; **alternative** uses an implemented adapter or an
explicit script; **unsupported** has no repository equivalent for that feature.

In Claude Code and OpenCode use `/plan`, `/implement`, `/lightweight`, `/inbox`, or
`/dispatch`. In Codex CLI use `$iw-plan`, `$iw-implement`, `$iw-lightweight`,
`$iw-inbox`, or `$iw-dispatch` followed by the same inputs, or select through `/skills`.
Codex skills require explicit invocation. Dispatch starts workers only when requested.

| Integration | Claude Code | Codex CLI | OpenCode V2 | Reviewed |
|-------------|-------------|-----------|-------------|----------|
| Shared repository instructions | **native**: CLAUDE.md imports AGENTS.md with Claude file-import syntax. ([CLAUDE.md](../../../CLAUDE.md), [AGENTS.md](../../../AGENTS.md)) | **native**: Loads AGENTS.md directly. ([AGENTS.md](../../../AGENTS.md)) | **native**: Loads AGENTS.md directly. ([AGENTS.md](../../../AGENTS.md)) | 2026-11-11 |
| Workflow: dispatch | **native**: Invoke /dispatch with workflow inputs. ([dispatch.md](../../../.claude/commands/dispatch.md)) | **alternative**: Invoke $iw-dispatch with workflow inputs; generated explicit-only skill. ([SKILL.md](../../../.agents/skills/iw-dispatch/SKILL.md), [openai.yaml](../../../.agents/skills/iw-dispatch/agents/openai.yaml)) | **alternative**: Invoke /dispatch; generated V2 command. ([opencode.json](../../../opencode.json)) | 2026-11-11 |
| Workflow: implement | **native**: Invoke /implement with workflow inputs. ([implement.md](../../../.claude/commands/implement.md)) | **alternative**: Invoke $iw-implement with workflow inputs; generated explicit-only skill. ([SKILL.md](../../../.agents/skills/iw-implement/SKILL.md), [openai.yaml](../../../.agents/skills/iw-implement/agents/openai.yaml)) | **alternative**: Invoke /implement; generated V2 command. ([opencode.json](../../../opencode.json)) | 2026-11-11 |
| Workflow: inbox | **native**: Invoke /inbox with workflow inputs. ([inbox.md](../../../.claude/commands/inbox.md)) | **alternative**: Invoke $iw-inbox with workflow inputs; generated explicit-only skill. ([SKILL.md](../../../.agents/skills/iw-inbox/SKILL.md), [openai.yaml](../../../.agents/skills/iw-inbox/agents/openai.yaml)) | **alternative**: Invoke /inbox; generated V2 command. ([opencode.json](../../../opencode.json)) | 2026-11-11 |
| Workflow: lightweight | **native**: Invoke /lightweight with workflow inputs. ([lightweight.md](../../../.claude/commands/lightweight.md)) | **alternative**: Invoke $iw-lightweight with workflow inputs; generated explicit-only skill. ([SKILL.md](../../../.agents/skills/iw-lightweight/SKILL.md), [openai.yaml](../../../.agents/skills/iw-lightweight/agents/openai.yaml)) | **alternative**: Invoke /lightweight; generated V2 command. ([opencode.json](../../../opencode.json)) | 2026-11-11 |
| Workflow: plan | **native**: Invoke /plan with workflow inputs. ([plan.md](../../../.claude/commands/plan.md)) | **alternative**: Invoke $iw-plan with workflow inputs; generated explicit-only skill. ([SKILL.md](../../../.agents/skills/iw-plan/SKILL.md), [openai.yaml](../../../.agents/skills/iw-plan/agents/openai.yaml)) | **alternative**: Invoke /plan; generated V2 command. ([opencode.json](../../../opencode.json)) | 2026-11-11 |
| Command descriptions and arguments | **native**: Description metadata and $ARGUMENTS expansion. ([plan.md](../../../.claude/commands/plan.md)) | **alternative**: Description becomes skill metadata; inputs come from the invocation message, without shell or macro expansion. ([SKILL.md](../../../.agents/skills/iw-plan/SKILL.md)) | **alternative**: Preserves description and $ARGUMENTS in generated command templates. ([opencode.json](../../../opencode.json)) | 2026-11-11 |
| Command argument hints | **native**: argument-hint metadata. ([plan.md](../../../.claude/commands/plan.md)) | **alternative**: Expected inputs appear in the skill body; no command hint UI adapter. ([SKILL.md](../../../.agents/skills/iw-plan/SKILL.md)) | **unsupported**: V2 command metadata has no argument-hint field; omitted. Canonical command documents inputs. ([plan.md](../../../.claude/commands/plan.md)) | 2026-11-11 |
| Shell allow/deny rules | **native**: Bash allow/deny settings. Commit approval remains a separate process requirement. ([settings.json](../../../.claude/settings.json), [AGENTS.md](../../../AGENTS.md)) | **unsupported**: No repository permission translation. Retains configured native sandbox/approvals; Claude wildcard rules are not equivalent to Codex execution prefixes. ([AGENTS.md](../../../AGENTS.md), [worker.py](../../../scripts/worker.py)) | **alternative**: Generated V2 shell rules preserve allow/deny ordering; no rule grants process approval. ([opencode.json](../../../opencode.json), [AGENTS.md](../../../AGENTS.md)) | 2026-11-11 |
| Native worktree creation hook | **native**: WorktreeCreate calls hook-create; Claude JSON name and CLAUDE_PROJECT_DIR feed the shared script. ([settings.json](../../../.claude/settings.json), [worktree.sh](../../../scripts/worktree.sh)) | **alternative**: No native hook adapter installed. Run scripts/worktree.sh new explicitly. ([worktree.sh](../../../scripts/worktree.sh)) | **alternative**: No native hook adapter installed. Run scripts/worktree.sh new explicitly. ([worktree.sh](../../../scripts/worktree.sh)) | 2026-11-11 |
| Native worktree removal hook | **native**: WorktreeRemove calls hook-remove with Claude worktree_path JSON. ([settings.json](../../../.claude/settings.json), [worktree.sh](../../../scripts/worktree.sh)) | **alternative**: No native hook adapter installed. Use scripts/worktree.sh rm/prune for lifecycle and Git safety. ([worktree.sh](../../../scripts/worktree.sh)) | **alternative**: No native hook adapter installed. Use scripts/worktree.sh rm/prune for lifecycle and Git safety. ([worktree.sh](../../../scripts/worktree.sh)) | 2026-11-11 |
| Backend configuration and generation | **native**: Canonical commands and settings live under .claude/. Hook definitions remain Claude-specific. ([settings.json](../../../.claude/settings.json)) | **alternative**: Generator installs repository skills in .agents/skills; no .codex/config.toml or execution rules are installed. ([sync-agent-config.py](../../../scripts/sync-agent-config.py)) | **alternative**: Generator writes opencode.json commands and permissions in V2 schema. ([sync-agent-config.py](../../../scripts/sync-agent-config.py), [opencode.json](../../../opencode.json)) | 2026-11-11 |
| Shared worktree layout and cleanup | **alternative**: Shared script supports explicit operations and Claude hook entry points. ([worktree.sh](../../../scripts/worktree.sh)) | **alternative**: Shared script is required for repository worktree operations. ([worktree.sh](../../../scripts/worktree.sh)) | **alternative**: Shared script is required for repository worktree operations. ([worktree.sh](../../../scripts/worktree.sh)) | 2026-11-11 |
| Worker launch and session identity | **alternative**: Uses --bg/--name/--session-id, then validates the returned job ID, conversation UUID, name and worktree in claude agents --json --all. ([worker.py](../../../scripts/worker.py)) | **alternative**: Creates an independent interactive task on the existing local shared app-server daemon; binds the returned ID before its first turn and validates native listing visibility, name, cwd, sandbox and approval routing. Never falls back to exec or a private server. ([worker.py](../../../scripts/worker.py)) | **alternative**: Creates a top-level V2 session on the UI shared service, verifies its identity/name/worktree and paginated native listing before command submission; optional --server is retained. ([worker.py](../../../scripts/worker.py)) | 2026-11-11 |
| Worker workflow dispatch | **alternative**: Launch prompt expands canonical /plan, /implement, or /lightweight template with worker-local gates. ([worker.py](../../../scripts/worker.py)) | **alternative**: Sends the expanded canonical prompt through turn/start on the recorded native task, after worktree protection and identity binding. ([worker.py](../../../scripts/worker.py)) | **alternative**: V2 session command endpoint runs the generated command. ([worker.py](../../../scripts/worker.py), [opencode.json](../../../opencode.json)) | 2026-11-11 |
| Worker agent and model selection | **alternative**: Separate --agent and --model; named background agents require Claude >= 2.1.191 and reject unavailable selections. ([worker.py](../../../scripts/worker.py)) | **unsupported**: Named-agent selection remains unsupported and is rejected; use explicit --default-agent. CODEX_THREAD_ID or --parent-session inherits the current task configuration/model through a native fork; optional --model overrides it. Forked tasks are independent, not child subagents. ([worker.py](../../../scripts/worker.py), [parallel.md](../../../docs/agents/protocols/parallel.md)) | **alternative**: Checks primary-capable named agent and independently forwards provider/model with optional variant. ([worker.py](../../../scripts/worker.py)) | 2026-11-11 |
| Worker resume and attachment | **alternative**: Protects lifecycle before claude attach with recorded background job ID. ([worker.py](../../../scripts/worker.py)) | **alternative**: Protects lifecycle and verifies effective native cwd/sandbox/approval routing, then opens the exact task on its recorded socket using codex --remote. Legacy exec workers retain explicit-ID CLI resume; ignored loaded-task overrides fail without changing the recorded mode. ([worker.py](../../../scripts/worker.py)) | **alternative**: Protects lifecycle, verifies native session visibility, and reopens the recorded session on the same shared service, forwarding any recorded --server. ([worker.py](../../../scripts/worker.py)) | 2026-11-11 |
| Worker session deletion | **alternative**: claude rm deletes the recorded background job; saved conversation transcript is retained. ([worker.py](../../../scripts/worker.py)) | **alternative**: Native task cleanup sends thread/delete for the exact recorded UUID to its recorded daemon socket. Legacy exec workers retain codex delete --force; no broad session discovery. ([worker.py](../../../scripts/worker.py)) | **alternative**: V2 DELETE targets only the recorded session, forwarding any recorded --server. ([worker.py](../../../scripts/worker.py)) | 2026-11-11 |
| Worker lifecycle, approval and cleanup | **alternative**: Shared status file and script checks; approval gates are workflow instructions, not permission enforcement. ([parallel.md](../../../docs/agents/protocols/parallel.md), [worker.py](../../../scripts/worker.py)) | **alternative**: Status validates persisted sandbox and native transport identity against the registry; native execution uses on-request approvals with automatic review. Sandbox danger-full-access removes filesystem/network enforcement; workflow plan/commit gates remain separate. ([parallel.md](../../../docs/agents/protocols/parallel.md), [worker.py](../../../scripts/worker.py)) | **alternative**: Shared status file and script checks; approval gates are workflow instructions, not permission enforcement. ([parallel.md](../../../docs/agents/protocols/parallel.md), [worker.py](../../../scripts/worker.py)) | 2026-11-11 |
| Native Codex task control transport | **unsupported**: Codex-specific transport is not applicable. Claude uses native background sessions and agent view instead. ([worker.py](../../../scripts/worker.py)) | **alternative**: Local shared-daemon WebSocket JSON-RPC over a Unix control socket; checks native interactive listing after initial user-message persistence. Requires a running UI daemon. Remote WebSocket servers are unsupported: run on the UI server host and select its local socket. UI tasks are independent; no subagent parentage is claimed. ([worker.py](../../../scripts/worker.py), [parallel.md](../../../docs/agents/protocols/parallel.md)) | **unsupported**: Codex-specific transport is not applicable. OpenCode uses its shared V2 service and native session inventory instead. ([worker.py](../../../scripts/worker.py)) | 2026-11-11 |

## Evidence

- **claude-instructions**: https://code.claude.com/docs/en/memory
- **claude-commands**: https://code.claude.com/docs/en/slash-commands
- **claude-hooks**: https://code.claude.com/docs/en/hooks
- **claude-permissions**: https://code.claude.com/docs/en/settings
- **codex-instructions**: https://developers.openai.com/codex/guides/agents-md/
- **codex-skills**: https://learn.chatgpt.com/docs/build-skills
- **codex-rules**: https://learn.chatgpt.com/docs/agent-configuration/rules
- **opencode-instructions**: https://opencode.ai/docs/instructions/
- **opencode-commands**: https://opencode.ai/docs/commands/
- **opencode-permissions**: https://opencode.ai/docs/permissions/
- **opencode-schema**: https://opencode.ai/config.json
- **repository-tests**: scripts/tests/test_worker.py and scripts/tests/test_worktree_prune.py; CLI/API mocks, not live sessions. Installed audit versions: Claude Code 2.1.322, Codex CLI 0.167.1, OpenCode v2.2.5.
- **codex-sandbox**: https://learn.chatgpt.com/docs/sandboxing
- **codex-app-server**: https://learn.chatgpt.com/docs/app-server
- **claude-agent-view**: https://code.claude.com/docs/en/agent-view
- **opencode-sdk**: https://opencode.ai/docs/build/sdk
- **worker-live-visibility**: Local smoke evidence on 2026-11-11: Codex native task was listed by the current TUI and executed pwd/branch checks in its assigned worktree; OpenCode created, read, listed, and deleted a sibling-worktree session without a model turn. Claude native listing shape checked against the installed CLI; new launch validation uses mocked regression tests.

## Maintenance

Follow [ADR-0014](../adr/0014-cover-harness-integrations-across-supported-backends.md).
Edit the compatibility manifest and canonical Claude commands/settings, then run
`python3 scripts/sync-agent-config.py`. Do not edit generated skills, this matrix, or
`opencode.json`. The same script with `--check` runs through documentation checks in CI.
The generator-owned file list is `.agents/generated-skills.json`; regeneration removes
obsolete owned files only when their previous content is intact, preserving other skills.

When changing backend CLI/API code, hooks, or permissions, review every affected backend
and update the reviewed input hashes and affected review dates in the manifest. Revalidate
official sources when upgrading a backend. Dates record evidence review, not an expiry or
a guarantee that CI detects upstream changes. Record newly introduced configuration files
and mechanisms in the same change; review is still needed for integrations outside the
known directories and worker operations. Local Claude settings are developer-owned and
are excluded. Generated `iw-*` skill paths are reserved for the generator.

AGENTS.md approval gates are process instructions. They do not enforce a shell permission
boundary. Codex retains its configured sandbox/approval policy; Claude shell wildcards
are not converted into Codex execution prefixes. No repository Codex permission adapter
or native worktree-hook adapter is installed. In Codex and OpenCode create and remove
worktrees through `scripts/worktree.sh`, including for workers; native worktree lifecycle
operations bypass this repository's layout and cleanup checks.

Worker launch/resume/delete adapters are covered by mocked CLI/API regression tests.
The recorded versions are audit context, not a claim that live backend sessions were
launched or deleted during verification. Keep lifecycle requirements in
[Protocol parallel](../protocols/parallel.md).
