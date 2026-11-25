---
title: Close remaining SBOM coverage gaps
kind: tech-debt
area: build
added: 2026-11-25
status: later
size: M
blocked-by: []
---

The GitHub dependency graph audit found no entries for OpenTofu providers, Helmfile charts and
rendered images, pinned remote CRDs, or standalone tooling downloaded by CI scripts. The pnpm
workspace project dependencies were present even though the package manager and platform binaries
from the pnpm lockfile's boot document were absent. Packages inside container layers require
image SBOMs.

The OpenTofu and Helm inputs already record package sources and versions in the provider
lockfiles and Helmfile. Verify native discovery or an accurate submission mapping before adding
those artifacts to the graph inventory. Evaluate a separate SBOM where GitHub cannot represent
the source and version accurately. See
[ADR-0029](../adr/0029-review-sbom-coverage-when-adding-third-party-tools-and-dependencies.md).
