---
title: Name temporary plan storage clearly and document its actual lifecycle
kind: tech-debt
area: docs
added: 2026-11-16
status: ready
size: M
blocked-by: []
---

Rename `docs/agents/projects/` to `docs/agents/plans/` to describe its actual contents. The
current workflow creates plans and deletes them before the implementation commit; when plans
are never committed separately, Git history does not preserve them. Keep plans temporary and
uncommitted, remove inaccurate history claims, and clarify that specs and ADRs preserve lasting
behavior and decisions. Update instructions, protocols, templates, tooling, tests and worker
plan-path examples consistently, preserving existing workers' approval gates and plan references.
Regenerate affected backend integrations from their canonical definitions. Coordinate terminology
and path updates with [rename-inbox-to-backlog.md](rename-inbox-to-backlog.md).
