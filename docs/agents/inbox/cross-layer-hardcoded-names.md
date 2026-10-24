---
title: Names hardcoded on both sides of Terraform, k8s and workflows
kind: tech-debt
area: deployment
added: 2026-10-24
---

Unlike `installation-values.md` (values a fork must change), these are internal names that must
simply match across layers, and nothing checks that they do:
- Namespace `invoice-watcher-dev`: literal in `ephemereal/main.tf:49` (`app_namespace`),
  `k8s-apps/environments/dev/kustomization.yml:3`, `cluster-init/templates/apps/invoice-watcher.yml:16`.
- Workload identity: `modules/gke/workload-identity.tf:4` (`ns/cluster-init/sa/secret-sync-sa`)
  and `:14` (`sa/app-sa`) vs `secret-sync-sa.yml` and `app-identity/service-account.yml`.
- Gateway address `gke-gateway-ip`: `modules/gke/cluster.tf:43` vs `gateway.yml:19`.
- `infrastructure.yml:63-70` does not pass the zone and cluster name to tofu, while
  `k8s-install.yml` reads them from the variables `GCP_ZONE` and `GCP_GKE_CLUSTER_NAME`.

Options: pass them as Terraform outputs / variables into helmfile values, or add a check to
`scripts/check-deployments.sh`.
