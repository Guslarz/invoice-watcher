---
id: ADR-0005
status: accepted
date: 2026-10-13
---

# ADR-0005: Derive versions from git tags

## Context

Versions written in build files drift and cause merge conflicts.

## Decision

The version comes from git tags via axion-release (`scmVersion`). Releases are made only by the
`Release` workflow, which creates the tag; between releases builds use the snapshot version.
Environments run the `snapshot` image.

## Consequences

- No version bumps in pull requests.
- A local build needs the tags (`git fetch --tags`) to get the right version.
