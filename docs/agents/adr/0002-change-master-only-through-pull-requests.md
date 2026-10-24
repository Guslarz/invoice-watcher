---
id: ADR-0002
status: accepted
date: 2026-10-13
---

# ADR-0002: Change master only through pull requests

## Context

`master` is the main development branch. Pushing to it deploys a snapshot image and syncs the
documentation site; ArgoCD tracks it for application manifests.

## Decision

Never commit or push directly to `master`. Every change, including documentation and tooling,
goes on its own branch created from an up-to-date `master` and ends with a pull request to
`master`. Agents open pull requests but never merge them.

## Consequences

CI (`build-and-analyze`, `docs`) runs on every change before it reaches `master`.
