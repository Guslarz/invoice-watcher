---
id: SPEC-RELEASE
title: Build and release
status: implemented
audience: [operator, developer]
modules:
  - buildSrc
  - images/mail-producer-docker
---

# Build and release

## Purpose

Every change is checked before it reaches `master`, every change on `master` produces an image
the dev environment can run, and a release produces a versioned image and a GitHub release.

## Behavior

- **RELEASE-001** Every pull request that changes a file outside `docs/` runs
  `./gradlew build analyze`: compilation, tests, Checkstyle and SpotBugs. Every pull request checks
  that the generated `docs/reference/` is up to date. Every pull request that changes a file under
  `deployments/` checks the deployment files: OpenTofu files are formatted and valid
  (`tofu fmt -check`, `tofu validate`), the helmfile and the kustomize environments render and the
  rendered manifests match their Kubernetes schemas. Every pull request checks that every
  `INVOICE_WATCHER_*` variable in a kustomize `.env` file binds to a property in the generated
  configuration reference. Every pull request that changes a file under `deployments/` or
  `.github/workflows/` checks that the names shared by OpenTofu, the Kubernetes manifests and the
  workflows match: the application namespace, the ServiceAccounts bound by workload identity and
  the gateway address. A failure blocks the change.
- **RELEASE-002** Every push to `master` that changes a file outside `docs/` builds and publishes
  the image `ghcr.io/guslarz/invoice-watcher/mail-producer:snapshot`, replacing the
  previous snapshot.
- **RELEASE-003** The `Release` workflow (run manually with `major`, `minor` or `patch`) increments
  the version, tags the commit `v<version>`, publishes the image tagged `<version>` and creates
  the GitHub release `v<version>`.
- **RELEASE-004** The project version is derived from git tags; it is never written in build files.
  Builds between releases get a snapshot version of the next patch release.
  Helm charts installed from the repository by path are not released and carry the fixed
  version `0.0.0`.
- **RELEASE-005** The mail producer image runs the application on Java 25 (Eclipse Temurin JRE)
  and carries OCI labels with the source repository, version and build time.

## Acceptance

- **RELEASE-001** Given a pull request changing only files under `docs/`, when it is opened, then
  the `build` job of `Build and analyze` is skipped and the `reference` job runs.
- **RELEASE-001** Given a pull request changing any file outside `docs/`, when it is opened, then
  both `build` and `reference` run.
- **RELEASE-001** Given a kustomize `.env` with `INVOICE_WATCHER_FOO_BAR` (no such property), when
  a pull request is opened, then the `reference` job of `Build and analyze` fails.
- **RELEASE-001** Given a pull request with a misindented or invalid `.tf` file under
  `deployments/infrastructure/`, when it is opened, then the `deployments` job of
  `Build and analyze` fails.
- **RELEASE-001** Given a pull request with a manifest field unknown to its schema under
  `deployments/k8s/` or `deployments/k8s-apps/`, when it is opened, then the `deployments` job
  fails.
- **RELEASE-001** Given a pull request renaming `secret-sync-sa` only in the cluster-init chart,
  when it is opened, then the `deployments` job of `Build and analyze` fails and names both files.
  The same holds for the application namespace (`app_namespace` in OpenTofu, the environment's
  `kustomization.yml`, the ArgoCD Application), `app-sa` and `gke-gateway-ip`.
- **RELEASE-001** Given a pull request changing only `.github/workflows/`, when it is opened, then
  the `deployments` job runs.
- **RELEASE-001** Given a pull request touching neither `deployments/` nor `.github/workflows/`,
  when it is opened, then the `deployments` job is skipped.
- **RELEASE-002** Given a pull request changing only files under `docs/`, when it is merged, then
  `Snapshot` does not run.
- **RELEASE-002** Given a pull request is merged, when the `Snapshot` workflow finishes, then
  pulling `...invoice-watcher/mail-producer:snapshot` gives an image whose version label matches
  the build.
- **RELEASE-003** Given the latest release is `v1.2.0`, when `Release` runs with `minor`, then
  tag `v1.3.0`, image tag `1.3.0` and GitHub release `v1.3.0` exist.

## Out of scope

- Deploying releases to an environment — environments run `snapshot` (DEPLOY-031).
- Publishing the `secret-helper` tool as a binary or image; it is run from the sources.
