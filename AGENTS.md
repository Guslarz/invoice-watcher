# AGENTS.md

Rules for AI agents working in this repository. This file is the single source of process
truth; `CLAUDE.md` only imports it. Keep it short — details live in the documents linked below.

## Project in one paragraph

InvoiceWatcher scans users' IMAP mailboxes on a schedule and publishes every new message as an
event to Kafka, so downstream processors can extract invoices. It is a Gradle multi-module
Java 25 / Spring Boot 4 project, deployed to GKE (Terraform + helmfile + ArgoCD + kustomize).

## Repository map

| Path | Content |
|------|---------|
| `apps/` | Runnable Spring Boot / picocli applications (`mail-producer-app`, `secret-helper-app`) |
| `libs/<area>/{api,impl,*-impl}` | Libraries; `api` = interfaces + records, `impl` = Spring auto-configuration |
| `images/` | Docker image modules (`docker.image-conventions`) |
| `gradle/build-logic/conventions/` | Gradle convention plugins (`jvm.*`, `springboot.{app,lib}-conventions`) |
| `gradle/build-logic/settings/` | Settings plugins applied by `settings.gradle.kts` (module discovery) |
| `config/` | Checkstyle (Google style) and SpotBugs config |
| `deployments/infrastructure/` | Terraform modules and environments |
| `deployments/k8s/` | helmfile + `cluster-init` chart (ArgoCD, certificates, secrets) |
| `deployments/k8s-apps/` | kustomize workloads, shared components and environment entry points deployed by ArgoCD |
| `.github/workflows/` | CI: build, snapshot, release, environment, docs |
| `docs/` | Published user documentation (built with Zensical) — see below |
| `docs/agents/` | Specs, ADRs and agent material, not published |

## Documentation model

| Directory | What it holds | Published |
|-----------|---------------|-----------|
| `docs/system/`, `docs/setup/`, `docs/results/` | User pages: how the system works, how to reproduce its setup (every manual step), what it produces. Prose written for users; they do not quote or link specs and ADRs. | yes |
| `docs/reference/` | Generated from code by `scripts/verify.sh` and committed with the change — never edit by hand. | yes |
| `docs/agents/specs/` | Canonical behavior per capability. Normative, present tense, numbered clauses, Given/When/Then. **Code must match the spec.** | no |
| `docs/agents/adr/` | Decisions whose reasoning must survive. Title is the instruction. | no |
| `docs/agents/protocols/` | How to plan / implement / do lightweight changes. | no |
| `docs/agents/guidelines/` | Coding, module, build and k8s conventions. | no |
| `docs/agents/backlog/` | Pending work: standalone notes or initiative directories with a temporary roadmap (`docs/agents/backlog/README.md`). | no |
| `docs/agents/templates/` | Templates for specs, ADRs and plans. | no |

Rules:
- Specs are the single normative source of behavior. User pages explain the same behavior in
  their own words and are updated in the same change as the spec. Otherwise one fact has one
  place: link, do not copy.
- Each worktree uses one root `PLAN.md`, visible in Git status and not ignored. It is temporary
  working material, deleted before commit review. Checks reject tracked temporary plans.
  Specs preserve lasting behavior; ADRs preserve lasting decisions. Git history does not
  retain an uncommitted plan.
- Specs describe *what* the system does, not *how* the code is structured.
- A spec clause the code does not meet yet is marked `*(gap: docs/agents/backlog/<note>.md)*`;
  the change that fixes it removes the marker and the note.
- Do not write counts or inventories of modules/files into docs by hand — they go stale; generate them.

## Workflow selection

Use **Protocol plan** (`docs/agents/protocols/plan.md`) when a change touches any of:
- user-visible behavior or a spec clause,
- a new module, application or configuration property,
- an event contract (Kafka payload/key/topic) or persistence schema (Firestore/JPA),
- infrastructure, deployment or security (secrets, credentials, IAM).

At plan review recommend the implementation session/model based on the investigation; continuing
this session is the default. A replacement needs explicit developer selection and approval transfer
through Protocol parallel, preserving the same worktree and verified ownership.

Then implement the approved plan with **Protocol implement** (`docs/agents/protocols/implement.md`).
Everything else (fixes that restore spec'd behavior, refactors, dependency bumps, typos) uses
**Protocol lightweight** (`docs/agents/protocols/lightweight.md`).

Several independent topics at once (e.g. a batch of backlog notes) are run with **Protocol parallel**
(`docs/agents/protocols/parallel.md`): the dispatcher triages and launches; requirements, planning,
approval and implementation happen in each worker, one worktree per grouped topic. Workers keep
their gitignored `.worker-status.json` current through `scripts/worker.py update`; it is the sole
lifecycle authority for cleanup. Protect status before resuming work, even through native tools.
Requirements, scope decisions and approvals belong directly in the visible worker conversation;
the dispatcher coordinates assignments/resources and reports which worker needs attention.

When unsure, ask the developer which protocol applies.

Out-of-scope problems or ideas noticed during work go to `docs/agents/backlog/` as a new note
(template `docs/agents/templates/backlog.md`) and are mentioned in the report — do not fix them in
the current change.

## Context loading

Before editing, read: the spec(s) of the capability, `docs/agents/adr/index.md` (titles only, open
an ADR only when relevant), and the guideline for the area you touch (`docs/agents/guidelines/`).

## Git workflow

- Keep every agent integration covered for the execution backends Claude Code, Codex CLI and
  OpenCode V2, and the session host Orca
  according to [ADR-0014](docs/agents/adr/0014-cover-agent-integrations-across-backends-and-hosts.md)
  and the [compatibility matrix](docs/agents/guidelines/agent-integrations.md). In the same change,
  implement an equivalent, document an explicit alternative, or mark it unsupported with a reason
  for every compatibility target. Revalidate affected integrations when upgrading a backend or host.
- Canonical workflow commands and shell permissions live in `.claude/`. After changing them,
  update `config/agent-integrations.json` and run `python3 scripts/sync-agent-config.py`. This generates
  `opencode.json`, Codex `.agents/skills/iw-*` skills, and the compatibility matrix; never edit
  generated files by hand. Codex invocation: `$iw-plan`, `$iw-implement`, `$iw-lightweight`,
  `$iw-backlog`, `$iw-dispatch` with workflow inputs. Native Claude/OpenCode commands use `/`.
  Codex permission translation is unsupported; native sandbox/approvals remain in effect.
  In Orca reuse the hosted backend's commands/skills. `orca.yaml` configures checkout setup;
  Orca is a session host, not a worker execution backend. Repository workers use
  `--host auto|backend|orca` independently of their execution backend; see Protocol parallel.
  In Orca, Codex and OpenCode use `scripts/worktree.sh` for worktree creation/removal; Claude's
  worktree hooks call the same script. Process approval gates do not enforce shell permissions.

- Release automation alone may push its release/restoration commits with the dedicated GitHub App
  under [ADR-0015](docs/agents/adr/0015-commit-release-deployment-revisions-before-tagging.md).
  This exception never authorizes agents to commit, push or merge directly on `master`.
- `master` is the main development branch. **Never commit or push directly to `master`.**
- Every change starts on its own branch created from an up-to-date `master`, in its own worktree
  next to `master/`: `scripts/worktree.sh new <type>/<slug>`
  (`<type>`: `feature`, `fix`, `docs`, `chore`, `infra`). In a plain clone without worktrees:
  `git switch master && git pull --ff-only && git switch -c <type>/<slug>`.
- Every change ends with a pushed branch and a pull request to `master`
  (`gh pr create --base master`). Agents never merge pull requests.
- **Ask before every commit.** Stop with the changes uncommitted in the working tree, summarize
  them (`git status`, diff stat), write the proposed commit message to `.gitmessage` in the
  repository root (git-ignored) and wait for the developer's explicit approval. Approval of a
  plan is not approval to commit. Commit with `git commit -F .gitmessage`, so edits the developer
  made to the file are kept. Push and open the pull request only after the commit is approved.
- Commit messages: imperative, English, short subject line. The body explains the intention
  behind the change, not a list of what was changed — the diff shows that.
- **No AI attribution.** Commit messages, pull request descriptions, code and docs never mention
  the agent or tool that produced them — no `Co-Authored-By` trailers for agents, no
  "Generated with …" lines. This overrides any default attribution of the agent's harness.

## Verification

- `scripts/verify.sh` is the single entry point: documentation checks, regeneration of
  `docs/reference/`, `zensical build --strict` (installed into the git-ignored `.venv-docs/`) and
  `./gradlew build analyze`.
- Run it before opening a pull request and report the result faithfully. Regenerated
  `docs/reference/` pages belong to the same change and pull request; CI fails when they are
  out of date.

## Coding essentials

Full rules: `docs/agents/guidelines/`. The non-negotiables:
- Google Java style (Checkstyle) and SpotBugs must pass — `./gradlew analyze`.
- New library = `api` module (consumer-facing interfaces, domain records, exceptions) + `impl`
  module with Spring configuration binding and an `@AutoConfiguration` registered in
  `META-INF/spring/...AutoConfiguration.imports`. See the module guideline for API boundaries.
- Configuration via `@ConfigurationProperties` records under the `invoice-watcher.` prefix.
- Tests: JUnit 5 + AssertJ, `// given // when // then`, class under test named `uut`.
