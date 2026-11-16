---
title: Strengthen validation and handoff in agent workflows
kind: tech-debt
area: build
added: 2026-11-16
status: ready
size: L
blocked-by: []
---

An audit of the current protocols and tooling found these follow-up opportunities. Split them
into independently reviewable changes during planning; coordinate overlapping protocol edits.

- Strengthen documentation validation: `scripts/check-docs.sh` checks spec document IDs but not
  behavior-clause uniqueness, requires plan `specs` without resolving those IDs, and checks
  dependency existence without detecting cycles. Agent Markdown links/anchors are outside the
  published site's validation. Add targeted checks and tests; handle new specs proposed inside
  plans explicitly. Remove drafting residue such as the clause-ID reminder in the implemented
  build-and-release spec. No missing Markdown file targets were found by the audit's basic scan.
- Align final report status with worker lifecycle. Protocol implement requires `done | blocked`
  even though ordinary stops are awaiting commit review or merge. Use lifecycle phases or
  distinguish report outcome from lifecycle explicitly; waiting for approval is normal.
- Record verification evidence against the reviewed revision, including uncommitted changes,
  commands, outcomes and skips. Current lifecycle detail is free text and can outlive the diff it
  describes. Define when changed scope requires renewed review or verification, keeping human
  approval distinct from script-managed lifecycle state. Preserve a concise handoff after the
  temporary plan is deleted; preserve the lifecycle documented in [Protocol plan](../protocols/plan.md).
- Show active topic assignments during inbox triage. `scripts/inbox.py ready` reads note
  frontmatter without consulting worker assignments, so an active note remains a launch
  candidate. Track normalized source-note/topic identities in workers and surface conflicts
  before dispatch, without treating topic assignment as a second cleanup lifecycle authority.
- Add an explicit implementation self-review before requesting commit approval: compare the
  diff with accepted requirements/specs, inspect failure paths and scope changes, and distinguish
  checked behavior from manual or IDE checks still pending. Passing tests alone do not establish
  that the requested result has been observed.

Spec-to-test coverage enforcement is already tracked in `remaining-spec-test-coverage.md`;
do not duplicate it here. Inbox directory terminology is tracked separately in
`rename-inbox-to-backlog.md`; temporary-plan storage is documented in [Protocol plan](../protocols/plan.md).
Update affected backend definitions and generated integrations
together when these follow-ups change workflow commands or worker adapters.
