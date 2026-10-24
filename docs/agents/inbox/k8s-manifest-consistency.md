---
title: Manifest inconsistencies in deployments
kind: bug
area: deployment
added: 2026-10-24
---

- The `version` label is hardcoded in the Kafka base (`:14`) but added by an overlay patch for
  mail-producer (`deployment-version-patch.yml`).
- The Kafka settings and image version are duplicated between `kafka/overlays/dev/config/.env` and
  `.run/files/kafka.yml` (see `run-configurations.md`).
- Indentation differs (`cluster-init/templates/certifiacates/gateway.yml:13`,
  `helmfile.yaml.gotmpl:42`); the deployment checks do not lint YAML style.
- `.yml` vs `.yaml`: `Chart.yaml`, `values.yaml` and `helmfile.yaml.gotmpl` are forced by the tools,
  everything else is `.yml`. Write that rule into `docs/agents/guidelines/deployments.md`.
