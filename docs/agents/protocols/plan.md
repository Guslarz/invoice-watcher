# Protocol plan

Use for changes that hit a planning trigger (see `AGENTS.md` → Workflow selection).
Input: a request from the developer or a note from `docs/agents/inbox/`.
Output: one plan file in `docs/agents/projects/`. **No production code.**

## Steps

1. Create the branch: `scripts/worktree.sh new <type>/<slug>` and work in the printed worktree
   (plain clone without worktrees: `git switch master && git pull --ff-only && git switch -c <type>/<slug>`).
2. Load context: affected spec(s) in `docs/specs/`, `docs/adr/index.md`, relevant guideline.
3. Copy `docs/agents/templates/plan.md` to `docs/agents/projects/<slug>.md` and fill it in:
   - **Goal** — one paragraph, user/operator perspective.
   - **Spec changes** — for each affected spec: clauses added / changed / removed, written exactly
     as they will appear in the spec. A new capability gets a new spec from
     `docs/agents/templates/spec.md`.
   - **Acceptance** — Given/When/Then per changed clause.
   - **Docs impact** — `BEHAVIOR` (end-user visible), `OPERATIONS` (deploy/config/infra),
     `INTERNAL` (neither). List the `system/` / `setup/` / `results/` pages to update.
   - **Design** — modules touched, new properties, contracts; enough for review, not code.
   - **ADR** — only when the reasoning must survive the change; draft title as an instruction.
   - **Open questions** — everything you would otherwise guess.
   - **Source** — the inbox note it comes from, if any.
4. Run `scripts/check-docs.sh`.
5. Stop. Present the plan path and the open questions to the developer and wait for approval.
   Do not commit unless the developer asks for it.
