---
title: Link application-local.yml into new worktrees
kind: idea
area: build
added: 2026-11-11
status: ready
size: S
blocked-by: []
---

Automatically create symbolic links to existing gitignored `application-local.yml` files when
creating a worktree through `scripts/worktree.sh`, so applications run with the developer's local
configuration without manually copying it for each branch. Cover both explicit creation and
Claude Code's WorktreeCreate hook through the shared creation path; Codex CLI and OpenCode use
the same script.

Identify the source checkout and corresponding relative application paths during planning.
Preserve existing destination files or links, handle absent source files explicitly, and keep
the configuration contents outside Git and logs. Document that edits through a link affect the
shared local configuration, and verify that worktree removal leaves the source files intact.
