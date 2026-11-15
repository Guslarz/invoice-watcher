---
id: ADR-0015
status: accepted
date: 2026-11-12
---

# ADR-0015: Commit release deployment revisions before tagging and restore development afterward

## Context

Environment already applies infrastructure and local bootstrap definitions from its workflow
checkout. ArgoCD needs an explicit revision in those definitions to keep a released deployment
on its release manifests instead of master. Image selection must agree with the selected release.

## Decision

Use the Gradle axion-release plugin as the authority for release version calculation and local
tag creation. Python orchestrates the Git commits and atomic push, without duplicating version
selection rules.

Commit the release tag as the Application's targetRevision and the release version as the
application image tags and pod version labels. Tag that commit. Create a later commit restoring
master, snapshot and the original development files. Push both commits and the tag atomically,
then publish artifacts from the tagged commit. Preserve tags on retries; identify a retry by its
original workflow source commit and increment stored in the release commit.

Use the dedicated release GitHub App to bypass master protection for this one automated sequence.
Keep contents-only installation access scoped to this repository. Configure that App explicitly
in the ruleset; ordinary changes, including changes made by agents, still require pull requests.
Do not force-push or bypass a concurrent master update. A stale dispatch fails before publication.

Environment uses the checked-out workflow ref for up and down. It installs cluster-init by path;
ArgoCD follows the targetRevision committed in that checkout, independently of the workflow ref.
An operator can therefore run Environment on a release tag to use the matching infrastructure,
bootstrap, application manifests and image. No separate deployment artifact or version input is
needed. Retain kustomize's base/overlays/components terminology; call the workload directory
workloads to distinguish it from ArgoCD Application resources.

## Consequences

Development continues following master and snapshot. Released deployments follow their tag and
versioned image. Destroying an environment requires selecting the appropriate workflow ref.
Restoring development before publication means a publication failure cannot leave master pinned
to an unreleased version. Re-running the failed workflow resumes publication from the same tag.

The App must be installed and its master ruleset exception configured before Release works.
This exception applies to release automation, not to human or agent commits. The release and
restoration commits bypass pull-request checks, while the tagged artifacts still run Gradle's
build and analysis before publication. There is no data restoration or guarantee of downgrade
compatibility when an operator runs older infrastructure definitions.

This amends ADR-0002's unconditional PR-only rule and ADR-0004's master-only deployment policy.
It also amends ADR-0005's snapshot-only environment policy; its tag-derived project versions
and unversioned local Helm chart remain in force.
