---
title: Rename the work inbox to backlog
kind: tech-debt
area: docs
added: 2026-11-16
status: ready
size: M
blocked-by: []
---

Rename `docs/agents/inbox/` to `docs/agents/backlog/` to reflect its role in tracking triaged
ideas, readiness, dependencies and initiative roadmaps. Update repository instructions,
protocols, templates, spec gap markers, links, tooling, tests and workflow command names
consistently. Preserve note identifiers relative to the collection and their dependencies;
entries remain proposals rather than delivery commitments. Update canonical workflow definitions
and the backend compatibility manifest, then regenerate Codex/OpenCode integrations instead of
editing generated files. Decide whether existing inbox commands need temporary compatibility
aliases during planning. Do not introduce a separate untriaged inbox as part of this rename.
