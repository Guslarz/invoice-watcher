# Deployments

Three layers, applied in this order by the `Environment` workflow (`.github/workflows/environment.yml`):

1. **Infrastructure** — `deployments/infrastructure`, applied with OpenTofu
   (`infrastructure.yml`). `environments/<env>/persistent` (project init, tfstate bucket, CI
   service account; applied once by hand) and `environments/<env>/ephemereal` (secrets, Firestore,
   GKE, Cloudflare DNS; brought up and down by the workflow). Reusable code goes to `modules/`.
2. **Cluster bootstrap** — `deployments/k8s/helmfile.yaml.gotmpl` (`k8s-install.yml`): ArgoCD,
   cert-manager, Cloudflare origin-ca-issuer, external-secrets and the local `cluster-init` chart
   (gateway, certificate, secret store, ArgoCD `Application`). Per-environment values in
   `environments/<env>.yml.gotmpl`.
3. **Applications** — `deployments/k8s-apps`, synced by ArgoCD from `master`
   (`environments/<env>/kustomization.yml`). Each app has `base/` and `overlays/<env>/`;
   configuration as env vars in `overlays/<env>/config/.env`; shared pieces as kustomize
   `components/`.

Rules:
- Pin every chart, provider and action version.
- Secrets never go into the repository — Secret Manager + external-secrets (ADR-0006).
- Every container declares probes, CPU/memory requests and a memory limit; no CPU limits.
- Changes under `deployments/` are a planning trigger (`AGENTS.md`) and impact `OPERATIONS`.
- Run `tofu fmt -recursive deployments/infrastructure` before committing.
- `scripts/check-deployments.sh` runs the same checks as the `deployments` CI job (OpenTofu
  formatting and validation, helmfile and kustomize rendering, kubeconform schema validation);
  a missing tool skips its check locally and fails it in CI (`CI` set).
