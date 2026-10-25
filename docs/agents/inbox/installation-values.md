---
title: Keep installation-specific values in one place
kind: tech-debt
area: deployment
added: 2026-10-13
---

The GitHub user (`Guslarz`), the domain (`guslarz.com`), the image name and the region, zone and
cluster name are repeated in several files: Terraform variables of `persistent` and `ephemeral`,
`deployments/k8s/environments/dev.yml.gotmpl`, `deployments/k8s/values/cluster-init.yml.gotmpl`,
`deployments/k8s-apps/components/app-identity/image-pull-secret.yml` and the dev overlay's
`kustomization.yml`, the `org.opencontainers.image.source` label in
`images/mail-producer-docker/src/Dockerfile`; zone and cluster name are also GitHub variables. Reproducing the setup under
another account means editing all of them (listed in `docs/setup/index.md`).

Consider deriving them from GitHub variables passed to OpenTofu and helmfile (as
`GCP_PROJECT_ID` already is), so a fork needs only repository settings. Kustomize files cannot
take variables, so the image name and pull-secret user may need a helmfile/ArgoCD parameter or a
generated overlay.
