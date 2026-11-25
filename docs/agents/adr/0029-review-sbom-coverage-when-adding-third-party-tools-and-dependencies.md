---
id: ADR-0029
status: accepted
date: 2026-11-25
---

# ADR-0029: Review SBOM coverage when adding third-party tools and dependencies

## Context

GitHub discovers some lockfiles and workflow references automatically. Tools installed by
scripts, tools used only in CI or tests, and artifacts declared in deployment configuration can
enter the supply chain without appearing in the repository's exported SBOM.

## Decision

When adding a third-party tool or dependency to a build, test, CI, release, deployment, or
supported local workflow, record its source and version and check whether the repository SBOM
captures its resolved identity. Keep verified automatic discovery where it covers the component.
For a material omission with a supported mapping, extend the existing submission or inventory
step in the same change and verify the generated entry. If the repository graph cannot represent
the component accurately, record the gap and the reason in the tracked backlog and link any
separate inventory used to cover it. Include the evidence or the recorded gap in the change review.

## Consequences

Reviewers can tell whether a newly introduced tool will be visible in the GitHub SBOM and where
to find coverage for components GitHub cannot represent. Verify graph visibility and advisory
matching independently.
