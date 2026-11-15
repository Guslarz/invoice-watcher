---
id: SPEC-RELEASE
title: Build and release
status: implemented
audience: [operator, developer]
modules:
  - gradle/build-logic/conventions
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
  the gateway address. Every pull request checks installation-configuration drift. A failure blocks the change.
- **RELEASE-002** Every push to `master` that changes a file outside `docs/` builds and publishes
  the installation's mail-producer image tagged `snapshot`, replacing the
  previous snapshot.
- **RELEASE-003** The `Release` workflow (run manually on `master` with `major`, `minor` or `patch`)
  uses the Gradle axion-release plugin to determine the next version, creates a release commit with
  `targetRevision: v<version>` and the matching application image tag, tags that commit `v<version>`,
  using the plugin's local `createRelease` task, and creates a subsequent commit restoring
  `master` and `snapshot` for development. It pushes
  both commits and the tag atomically, then publishes the versioned image and GitHub release from
  the tagged commit. A retry of the same run reuses its existing release tag without moving it.
- **RELEASE-004** The project version is derived from git tags; it is never written in build files.
  Builds between releases get a snapshot version of the next patch release.
  Helm charts installed from the repository by path are not released and carry the fixed
  version `0.0.0`.
- **RELEASE-005** The mail producer image runs the application on Java 25 (Eclipse Temurin JRE)
  and carries OCI labels with the source repository, version and build time.
- **RELEASE-006** Every push to `master` builds the documentation site and publishes it at
  https://guslarz.github.io/invoice-watcher/. Every pull request builds the site in strict mode
  without publishing it; a broken link or a structure check failure blocks the change.
- **RELEASE-007** Ordinary changes to `master` require merging a pull request whose checks
  (RELEASE-001, RELEASE-006) passed or were skipped. Direct pushes, force pushes and deleting
  `master` are rejected for ordinary users, administrators and workflows. The dedicated release
  GitHub App is the sole configured bypass and is used only for the release workflow's atomic
  fast-forward push of the release commit, development restoration commit and release tag.

## Acceptance

- **RELEASE-003** Given release version 1.3.0, when preparation finishes, then v1.3.0 points to
  the release commit with targetRevision v1.3.0, image tag 1.3.0 and pod label 1.3.0; master
  points to its subsequent restoration commit with the original development files restored.
- **RELEASE-003** Given artifact publication fails after the refs are pushed, when the same
  workflow run is retried, then it checks out its existing release tag without moving it.
- **RELEASE-003** Given master changed since dispatch, when a new release is prepared, then it
  fails before creating remote refs and tells the operator to dispatch from current master.
- **RELEASE-007** Given the server rejects the atomic release push, when preparation fails,
  then neither master nor the release tag changes remotely.

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
- **RELEASE-006** Given a change to a page under `docs/` is merged, when the `Docs` workflow
  finishes, then the page at https://guslarz.github.io/invoice-watcher/ shows the change.
- **RELEASE-006** Given a pull request with a broken link in `docs/`, when it is opened, then the
  `Docs` workflow fails and nothing is published.
- **RELEASE-007** Given a local commit on `master`, when it is pushed to GitHub, then the push is
  rejected.
- **RELEASE-007** Given a pull request whose `build` check failed, when it is viewed on GitHub,
  then it cannot be merged.

## Out of scope

- Publishing the `secret-helper` tool as a binary or image; it is run from the sources.
