---
title: Clean up stale worktrees when their number grows
kind: idea
area: build
added: 2026-10-22
---

Worktrees pile up next to `master/`: one per topic, plus the ones Claude Code creates for
subagents and workflow agents, which it never removes. Removal is manual
(`scripts/worktree.sh rm`), so after a few batches there are many stale directories, each with its
own `build/` output and Gradle daemon.

Idea: when Protocol plan or Protocol parallel starts and there are 10 or more worktrees (besides
`master`), clean up first. For example, a `scripts/worktree.sh prune` command removes every
worktree that `rm` considers safe: no uncommitted changes, and every commit merged into
`origin/master` or pushed with its pull request merged or closed. It also lists the rest with the
reason they were kept. The protocols would call it in their first step. To decide: whether the
threshold is a fixed 10 or configurable, whether the agent asks before removing or only reports,
and whether the matching `claude agents` sessions are removed too (`claude rm`).
