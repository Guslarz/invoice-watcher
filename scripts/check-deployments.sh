#!/usr/bin/env bash
# Deployment file checks, run by CI (Build and analyze / deployments) and scripts/verify.sh:
# YAML style (yamllint), OpenTofu formatting and validation, rendering of the helmfile and the kustomize environments,
# Kubernetes schema validation of the rendered manifests, the local Kafka image matching the cluster one,
# names shared by OpenTofu, the manifests and the workflows matching (scripts/check-names.sh).
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
cluster_kafka=deployments/k8s-apps/apps/kafka/base/statefulset.yml
local_kafka=.run/files/kafka.yml
kafka_image() { grep -oE 'image: apache/kafka:[^[:space:]]+' "$1" | cut -d' ' -f2 || true; }
cluster_image=$(kafka_image "$cluster_kafka")
local_image=$(kafka_image "$local_kafka")
if [[ -z "$cluster_image" || "$local_image" != "$cluster_image" ]]; then
  echo "FAIL: $local_kafka: image '$local_image' differs from '$cluster_image' in $cluster_kafka" >&2
  exit 1
fi

echo "==> Shared names"
scripts/check-names.sh

echo "==> YAML style"
# Helm and helmfile templates hold inline {{ ... }} expressions, which are not YAML; they are replaced
# by a placeholder so the templates are linted too, with their line numbers kept.
if command -v yamllint >/dev/null 2>&1; then
  yaml_failed=0
  while IFS= read -r file; do
    sed -E 's/\{\{[^}]*\}\}/x/g' "$file" \
      | yamllint --strict --format parsable --config-file deployments/.yamllint.yml - \
      | sed "s|^stdin:|$file:|" || yaml_failed=1
  done < <(find deployments \( -name '*.yml' -o -name '*.yaml' -o -name '*.gotmpl' \) \
    -not -path '*/.terraform/*' | sort)
  if [[ "$yaml_failed" -ne 0 ]]; then
    echo "FAIL: YAML style - see deployments/.yamllint.yml" >&2
    exit 1
  fi
else
  missing_tool yamllint
fi

echo "==> OpenTofu"
if command -v tofu >/dev/null 2>&1; then
  tofu fmt -check -recursive -diff deployments/infrastructure
  for root in deployments/infrastructure/environments/*/*/; do
    echo "--- $root"
    tofu -chdir="$root" init -backend=false -input=false -lockfile=readonly >/dev/null
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
    # The GCP_* variables are required by the values; placeholders are enough to render
    GCP_PROJECT_NUMBER="${GCP_PROJECT_NUMBER:-000000000000}" \
      GCP_PROJECT_ID="${GCP_PROJECT_ID:-placeholder-project}" \
      GCP_ZONE="${GCP_ZONE:-europe-west1-b}" \
      helmfile --file deployments/k8s/helmfile.yaml.gotmpl --environment "$env" template --quiet \
      | validate_manifests
  done
else
  missing_tool helmfile
fi
