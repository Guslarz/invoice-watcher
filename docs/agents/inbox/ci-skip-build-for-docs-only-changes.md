---
title: Skip the Gradle build in CI for documentation-only changes
kind: tech-debt
area: build
added: 2026-10-22
---

No workflow has a `paths` filter, so a change that touches only documentation runs the full
`./gradlew build analyze` in `.github/workflows/build-and-analyze.yml` on the pull request, and
after the merge `.github/workflows/snapshot.yml` builds and publishes `snapshot` Docker images to
ghcr.io although no code changed. `.github/workflows/docs.yml` is the only check such a change needs.

Proposal: add a `paths` filter to both workflows that excludes `docs/**`, `**/*.md`, `mkdocs.yml`,
`requirements-docs.txt` and `.claude/**`, but re-includes `docs/reference/**` (later patterns win),
because the build checks that the generated reference is up to date.

Caveat: a workflow skipped by a path filter never reports its check, so once `build` becomes a
required status check (`master-branch-protection.md`) docs-only pull requests would wait forever —
add a no-op job with the same check name for the skipped paths at that point.
