---
title: Version the deployment definitions and deploy a released version
kind: idea
area: deployment
added: 2026-10-22
---

Everything under `deployments/` is used straight from a branch: `.github/workflows/environment.yml`
runs the OpenTofu modules and helmfile from the ref the workflow was dispatched on, the local
`cluster-init` chart is installed by path with the fixed version `0.0.0` (RELEASE-004), and the
ArgoCD application in `deployments/k8s/cluster-init/templates/apps/invoice-watcher.yml` follows
`targetRevision: HEAD`. An environment therefore cannot be pinned to, or rolled back to, a known
version of its definitions, and the image (`snapshot`, DEPLOY-031) is versioned separately from
the manifests that run it.

Idea: version the deployment definitions together with the application, possibly with a moving
`snapshot` alongside releases (as images are, RELEASE-002/003): e.g. the `Release` workflow
packages and publishes the charts (OCI in ghcr.io) and the kustomize tree at the release tag,
and `environment.yml` takes a version input (`snapshot` or `<version>`) that selects the tag of
the charts, the OpenTofu modules (checkout of `v<version>`), ArgoCD's `targetRevision` and the
image. Doing this supersedes the "local charts are not versioned" consequence in ADR-0005 and
needs a new ADR.
