#!/usr/bin/env bash
# Deployment file checks, run by CI (Build and analyze / deployments) and scripts/verify.sh:
# OpenTofu formatting and validation, rendering of the helmfile and the kustomize environments,
# Kubernetes schema validation of the rendered manifests. A missing tool skips its check.
set -euo pipefail

cd "$(dirname "$0")/.."

# Custom resources are validated against the community CRD catalog; kinds without a schema anywhere
# are skipped (kubeconform -ignore-missing-schemas).
crd_catalog='https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'

validate_manifests() {
  if command -v kubeconform >/dev/null 2>&1; then
    kubeconform -strict -summary -ignore-missing-schemas \
      -schema-location default -schema-location "$crd_catalog" -
  else
    cat >/dev/null
    echo "SKIP: kubeconform not installed - manifests rendered but not validated" >&2
  fi
}

echo "==> OpenTofu"
if command -v tofu >/dev/null 2>&1; then
  tofu fmt -check -recursive -diff deployments/infrastructure
  for root in deployments/infrastructure/environments/*/*/; do
    echo "--- $root"
    tofu -chdir="$root" init -backend=false -input=false >/dev/null
    tofu -chdir="$root" validate
  done
else
  echo "SKIP: tofu not installed" >&2
fi

echo "==> Kustomize environments"
if command -v kubectl >/dev/null 2>&1; then
  for env in deployments/k8s-apps/environments/*/; do
    echo "--- $env"
    kubectl kustomize "$env" | validate_manifests
  done
else
  echo "SKIP: kubectl not installed" >&2
fi

echo "==> Helmfile environments"
if command -v helmfile >/dev/null 2>&1; then
  for env in deployments/k8s/environments/*.yml.gotmpl; do
    env=$(basename "$env" .yml.gotmpl)
    echo "--- $env"
    # GCP_PROJECT_NUMBER is required by the values; a placeholder is enough to render
    GCP_PROJECT_NUMBER="${GCP_PROJECT_NUMBER:-000000000000}" \
      helmfile --file deployments/k8s/helmfile.yaml.gotmpl --environment "$env" template --quiet \
      | validate_manifests
  done
else
  echo "SKIP: helmfile not installed" >&2
fi
