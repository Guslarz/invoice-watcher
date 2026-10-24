---
title: Reorganize the cluster-init chart templates
kind: tech-debt
area: deployment
added: 2026-10-24
---

`deployments/k8s/cluster-init/`:
- `templates/certifiacates` is misspelled and holds more than certificates: `gateway.yml`, the
  shared `secret-sync-sa.yml` (also used by `secrets/aes-key-secret.yml` and
  `secrets/github-pat-secret.yml`) and the Cloudflare token sync (`cloudflare-api-token.yml`, which
  belongs in `secrets/`). `certificate.yml` holds both the `OriginIssuer` and the `Certificate`.
  Helm loads templates recursively, so moving files changes nothing else.
- `secrets/cluster-secret-store.yml` also defines the `eso-secret-reader` ServiceAccount, Role and
  RoleBinding.
- Secret syncs are named `aes-key-secret`, `github-pat-secret` but `cloudflare-api-token`.
- `Chart.yaml` keeps the boilerplate description "A Helm chart for Kubernetes"; `values.yaml` is
  empty although the templates require `gcpProjectNumber`, `domain`, `environment`, `github.*`.
- `values/cluster-init.yml.gotmpl:6` sets `registry`, which no template uses.
- Values are passed three ways: inline in `helmfile.yaml.gotmpl`, `values/*.gotmpl` and the static
  `values/origin-ca-issuer.yml`. `values/.gitkeep` is redundant.
- `deployments/.gitignore:8` (`k8s/cluster-init/charts/argo-cd-*.tgz`) is stale — ArgoCD is a
  helmfile release now.
