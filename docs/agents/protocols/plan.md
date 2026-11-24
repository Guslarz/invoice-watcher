# Protocol plan

Use for changes that hit a planning trigger (see `AGENTS.md` → Workflow selection).
Input: a request from the developer or a note from `docs/agents/inbox/`.
Output: one temporary, uncommitted `PLAN.md` at the worktree root.
**No production code.**

Plan files are local working material and are not committed separately. Delete the implementation
plan before commit review. Keep it unignored so it appears in the changes view. Documentation
and PR checks reject tracked temporary plans; an untracked plan is allowed locally. Specs
preserve lasting behavior; ADRs preserve lasting decisions.
Git history does not preserve a plan that was never committed.

## Steps

1. An assigned parallel worker reuses its branch/worktree: do not prune or create another.
   Otherwise run `scripts/worktree.sh prune` and report the worktrees it kept (it does nothing below 10
   worktrees besides `master`). Create the branch: `scripts/worktree.sh new <type>/<slug>` and work
   in the printed worktree
   (plain clone without worktrees: `git switch master && git pull --ff-only && git switch -c <type>/<slug>`).
2. Load context: affected spec(s) in `docs/agents/specs/`, `docs/agents/adr/index.md`, relevant guideline.
3. Discuss requirements and resolve assumptions with the developer in this session. A registered
   worker sets `discussing`, then `planning`, using `python3 scripts/worker.py update <name> --phase <phase>`.
   Requirements and approvals are addressed to the developer in this visible worker
   conversation, then pause here. Only assignment/shared-resource conflicts go to the dispatcher;
   completion reports identify attention needed without relaying decisions.
   Copy `docs/agents/templates/plan.md` to `PLAN.md` and fill it in:
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
   - **Implementation recommendation** — continue this session by default, or recommend a
     specific available backend/model after investigation. Explain remaining uncertainty,
     rationale and escalation conditions; respect explicit developer preferences. Use no fixed
     model ranking or automatic downgrade. Include this recommendation in developer review;
     dispatch selects only the initial planning model.
   - **Source** — the inbox note it comes from, if any.
4. Run `scripts/check-docs.sh`.
5. Stop. Present the plan path and the open questions to the developer and wait for approval.
   A registered worker sets `awaiting-plan-approval` with `--plan <path>`. Approval must occur in
   this worker session; dispatch/resume is not approval. After approval follow Protocol implement
   in the same worktree/session by default. A selected replacement requires explicit developer
   approval transfer through the handoff procedure in Protocol parallel; plan approval alone
   does not select or authorize a replacement. Do not commit unless separately approved.
