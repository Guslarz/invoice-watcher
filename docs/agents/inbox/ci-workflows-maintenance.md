---
title: Deduplicate and fix the CI workflows
kind: tech-debt
area: build
added: 2026-10-24
---

`.github/workflows/`:
- Duplication: checkout + `setup-java` (temurin 25) + `setup-gradle` in `build-and-analyze.yml`
  (twice), `release.yml`, `snapshot.yml`; the `build analyze publish -P...docker...` command in
  `release.yml:52-56` and `snapshot.yml:30-35`; GCP auth in `infrastructure.yml:72-76` and
  `k8s-install.yml:20-24`; tofu/helmfile/helm versions in three workflows, synced only by a
  comment (`build-and-analyze.yml:65`). A composite action would hold them once.
- Pinning: actions use major tags (`@v6`), the deployments guideline asks to pin every action
  version; `actions/create-release@v1` (`release.yml:62`) is archived.
- `build-and-analyze.yml:94`: the `reference` job has no `needs`/`if`, so documentation-only PRs
  still run Gradle, contrary to the comment at `:7-8`. It also re-implements part of
  `scripts/verify.sh`, while the `.env` relaxed-binding check (`verify.sh:20-36`) runs only locally.
- `infrastructure.yml`: for `down` the `tofu plan` step shows an apply plan; the plan is not saved,
  so apply plans again; `:46-54` checks only `AES_KEY`, not the PAT or the Cloudflare token.
- `k8s-install.yml:51-52` installs `helm-diff` but runs `sync`; the helmfile step has no `name`.
- Verify: `release.yml:27` fetches tags with the default `fetch-depth: 1` — axion-release may need
  full history; `check-deployments.sh` may silently skip the kustomize check if `kubectl` is
  missing on the runner.
- Minor: "environmental variables" step names, `github.event.inputs.*` instead of `inputs.*` in
  `environment.yml`, no trailing newline in `snapshot.yml`.
