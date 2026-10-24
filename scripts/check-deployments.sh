#!/usr/bin/env bash
# Deployment file checks, run by CI (Build and analyze / deployments) and scripts/verify.sh:
# OpenTofu formatting and validation, rendering of the helmfile and the kustomize environments,
# Kubernetes schema validation of the rendered manifests, the local Kafka image matching the cluster one.
# A missing tool skips its check locally and fails it in CI (when CI is set).
set -euo pipefail

cd "$(dirname "$0")/.."

missing_tool() {
  if [[ -n "${CI:-}" ]]; then
    echo "FAIL: $1 not installed" >&2
    exit 1
  fi
  echo "SKIP: $1 not installed${2:+ - $2}" >&2
}

# Custom resources are validated against the community CRD catalog; kinds without a schema anywhere
# are skipped (kubeconform -ignore-missing-schemas).
crd_catalog='https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'

validate_manifests() {
  if command -v kubeconform >/dev/null 2>&1; then
    kubeconform -strict -summary -ignore-missing-schemas \
      -schema-location default -schema-location "$crd_catalog" -
  else
    cat >/dev/null
    missing_tool kubeconform "manifests rendered but not validated"
  fi
}

echo "==> Local Kafka image"
# .run/files/kafka.yml takes its environment from the dev overlay, but compose cannot read the image
# from kustomize - the tag is repeated and must match.
cluster_kafka=deployments/k8s-apps/apps/kafka/base/deployment.yml
local_kafka=.run/files/kafka.yml
kafka_image() { grep -oE 'image: apache/kafka:[^[:space:]]+' "$1" | cut -d' ' -f2 || true; }
cluster_image=$(kafka_image "$cluster_kafka")
local_image=$(kafka_image "$local_kafka")
if [[ -z "$cluster_image" || "$local_image" != "$cluster_image" ]]; then
  echo "FAIL: $local_kafka: image '$local_image' differs from '$cluster_image' in $cluster_kafka" >&2
  exit 1
fi

echo "==> OpenTofu"
if command -v tofu >/dev/null 2>&1; then
  tofu fmt -check -recursive -diff deployments/infrastructure
  for root in deployments/infrastructure/environments/*/*/; do
    echo "--- $root"
    tofu -chdir="$root" init -backend=false -input=false >/dev/null
    tofu -chdir="$root" validate
  done
else
  missing_tool tofu
fi

echo "==> Kustomize environments"
if command -v kubectl >/dev/null 2>&1; then
  for env in deployments/k8s-apps/environments/*/; do
    echo "--- $env"
    kubectl kustomize "$env" | validate_manifests
  done
else
  missing_tool kubectl
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
  missing_tool helmfile
fi
