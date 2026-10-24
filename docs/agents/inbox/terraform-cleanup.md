---
title: Remove unused Terraform resources and pin versions
kind: tech-debt
area: deployment
added: 2026-10-24
---

`deployments/infrastructure/`:
- Unused: `modules/gke/data.tf:1` `data "google_client_config" "default"`; variable `gcp_region` of
  `modules/secret` (still passed by `ephemereal/main.tf:30`); the `ghcr_docker_config_json` secret
  (`modules/secret/ghcr-docker-config-json.tf`) and its `github_registry` / `github_username`
  variables — the image pull secret is built in
  `k8s-apps/components/app-identity/image-pull-secret.yml` from `github-pat-secret`; empty
  `outputs.tf` in `cloudflare-domain`, `firestore`, `project-init`, `secret`.
- Versions: no `required_version`; `google` 7.43.0 is pinned only inside each module (four times),
  not in the root of `ephemereal`; `cloudflare` is pinned twice. `.terraform.lock.hcl` is
  git-ignored (`deployments/.gitignore`), so provider hashes are not locked.
- `persistent` has no `terraform {}` block; its state is local by design
  (`docs/setup/one-time.md`). Consider migrating it to the bucket it creates once that exists.
- API enablement: `project-init/api.tf` enables several APIs, other modules have a one-resource
  `*-api.tf` each. `compute.googleapis.com` (needed for the gateway address) is never enabled
  explicitly (verify).
- `project-init/github-actions-sa.tf:41,47`: resources `sa_role_firestore_develop_admin` and
  `sa_role_iam_admin` grant `roles/firebase.developAdmin` and `roles/iam.securityAdmin`.
- `firestore/default-db.tf:9` `deletion_policy = "DELETE"` — fine for an ephemeral environment,
  but worth a comment.
