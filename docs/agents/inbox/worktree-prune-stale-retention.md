---
title: Investigate worktree prune retaining stale or merged worktrees
kind: bug
area: build
added: 2026-11-11
status: later
size: M
blocked-by: []
---

`scripts/worktree.sh list` showed old worktrees with MERGED PRs, while `scripts/worktree.sh prune`
retained unregistered worktrees (`agent-neutral-workers`, `gradle-configuration-cache`,
`inbox-triage`, `kafka-volume`, `opencode-config`, `public-repo-pages`) and `unknown-worker`
worktrees (`build-logic-conventions`, `checkstyle-unnamed-variables`, `installation-values`,
`reference-enum-values`, `remove-dependabot-alerts-note`).

Future investigation should distinguish intended safety retention from stale registry/session
discovery or cleanup usability bugs. This note records the observed symptom only and does not claim
a proven root cause.
