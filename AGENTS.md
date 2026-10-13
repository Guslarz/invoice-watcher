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
| `images/` | Docker image modules (`docker/image-conventions`) |
| `buildSrc/` | Gradle convention plugins (`java-*`, `spring-boot-{app,lib}-conventions`) |
| `config/` | Checkstyle (Google style) and SpotBugs config |
| `deployments/infrastructure/` | Terraform modules and environments |
| `deployments/k8s/` | helmfile + `cluster-init` chart (ArgoCD, certificates, secrets) |
| `deployments/k8s-apps/` | kustomize bases/overlays deployed by ArgoCD |
| `.github/workflows/` | CI: build, snapshot, release, environment, docs |
| `docs/` | Published documentation (MkDocs) — see below |
| `docs/agents/` | Agent-only material, not published |

## Documentation model

| Directory | What it holds | Published |
|-----------|---------------|-----------|
| `docs/specs/` | Canonical behavior per capability. Normative, present tense, numbered clauses, Given/When/Then. **Code must match the spec.** | yes |
| `docs/adr/` | Decisions whose reasoning must survive. Title is the instruction. | yes |
| `docs/guide/`, `docs/operate/`, `docs/develop/` | Narrative pages for end users, operators, developers. Reuse spec clauses via snippets instead of restating them. | yes |
| `docs/reference/` | Generated from code by `scripts/verify.sh` and committed with the change — never edit by hand. | yes |
| `docs/agents/protocols/` | How to plan / implement / do lightweight changes. | no |
| `docs/agents/guidelines/` | Coding, module, build and k8s conventions. | no |
| `docs/agents/projects/` | Temporary plans, one per change; deleted by the PR that implements them. | no |
| `docs/agents/inbox/` | Ideas and TODOs for later, one note per file (`docs/agents/inbox/README.md`). | no |
| `docs/agents/templates/` | Templates for specs, ADRs and plans. | no |

Rules:
- One fact has one place. Link or include (`--8<--`), do not copy.
- Specs describe *what* the system does, not *how* the code is structured.
- A spec clause the code does not meet yet is marked `*(gap: docs/agents/inbox/<note>.md)*`;
  the change that fixes it removes the marker and the note.
- Do not write counts or inventories of modules/files into docs by hand — they go stale; generate them.

## Workflow selection

Use **Protocol plan** (`docs/agents/protocols/plan.md`) when a change touches any of:
- user-visible behavior or a spec clause,
- a new module, application or configuration property,
- an event contract (Kafka payload/key/topic) or persistence schema (Firestore/JPA),
- infrastructure, deployment or security (secrets, credentials, IAM).

Then implement the approved plan with **Protocol implement** (`docs/agents/protocols/implement.md`).
Everything else (fixes that restore spec'd behavior, refactors, dependency bumps, typos) uses
**Protocol lightweight** (`docs/agents/protocols/lightweight.md`).

When unsure, ask the developer which protocol applies.

Out-of-scope problems or ideas noticed during work go to `docs/agents/inbox/` as a new note
(template `docs/agents/templates/inbox.md`) and are mentioned in the report — do not fix them in
the current change.

## Context loading

Before editing, read: the spec(s) of the capability, `docs/adr/index.md` (titles only, open an ADR
only when relevant), and the guideline for the area you touch (`docs/agents/guidelines/`).

## Git workflow

- `master` is the main development branch. **Never commit or push directly to `master`.**
- Every change starts on its own branch created from an up-to-date `master`:
  `git switch master && git pull --ff-only && git switch -c <type>/<slug>`
  (`<type>`: `feature`, `fix`, `docs`, `chore`, `infra`).
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
  `docs/reference/`, `mkdocs build --strict` and `./gradlew build analyze`.
- Run it before opening a pull request and report the result faithfully. Regenerated
  `docs/reference/` pages belong to the same change and pull request; CI fails when they are
  out of date.

## Coding essentials

Full rules: `docs/agents/guidelines/`. The non-negotiables:
- Google Java style (Checkstyle) and SpotBugs must pass — `./gradlew analyze`.
- New library = `api` module (interfaces, records, properties) + `impl` module with an
  `@AutoConfiguration` registered in `META-INF/spring/...AutoConfiguration.imports`.
- Configuration via `@ConfigurationProperties` records under the `invoice-watcher.` prefix.
- Tests: JUnit 5 + AssertJ, `// given // when // then`, class under test named `uut`.
