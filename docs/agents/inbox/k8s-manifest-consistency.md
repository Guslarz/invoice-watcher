---
title: Kafka container is named mail-producer, and other manifest inconsistencies
kind: bug
area: deployment
added: 2026-10-24
---

- `deployments/k8s-apps/apps/kafka/base/deployment.yml:17`: the Kafka container is named
  `mail-producer` (copy-paste). `kubectl logs -c` and dashboards show the wrong name.
- The `version` label is hardcoded in the Kafka base (`:14`) but added by an overlay patch for
  mail-producer (`deployment-version-patch.yml`).
- Indentation differs (`cluster-init/templates/certifiacates/gateway.yml:13`,
  `helmfile.yaml.gotmpl:42`); the deployment checks do not lint YAML style.
- `.yml` vs `.yaml`: `Chart.yaml`, `values.yaml` and `helmfile.yaml.gotmpl` are forced by the tools,
  everything else is `.yml`. Write that rule into `docs/agents/guidelines/deployments.md`.
