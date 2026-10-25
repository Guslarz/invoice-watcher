#!/usr/bin/env bash
# Names shared across layers stay literal in every file that uses them (kustomize cannot take
# variables), so a rename on one side only must fail here instead of on the next `up`: the
# application namespace, the ServiceAccounts bound by workload identity, the gateway address and
# Kafka's data disk.
# Run by scripts/check-deployments.sh.
set -euo pipefail

cd "$(dirname "$0")/.."

infra=deployments/infrastructure
k8s=deployments/k8s
apps=deployments/k8s-apps
identity=$infra/modules/gke/workload-identity.tf

# First value captured by the sed ERE (group 1), optionally within a sed address range
value() {
  local file=$1 pattern=$2 range=${3:-}
  sed -nE "${range} s/${pattern}/\1/p" "$file" | head -n 1
}

failures=0

# Every place must hold the same, non-empty name: place = "<label>" "<value>", the first one is
# the reference
same() {
  local what=$1 ref_place=$2 ref=$3
  shift 3
  if [[ -z "$ref" ]]; then
    echo "FAIL: $what not found in $ref_place" >&2
    failures=$((failures + 1))
    return
  fi
  while (($#)); do
    if [[ "$2" != "$ref" ]]; then
      echo "FAIL: $what '${2:-<not found>}' in $1 differs from '$ref' in $ref_place" >&2
      failures=$((failures + 1))
    fi
    shift 2
  done
}

echo "--- application namespace"
argocd_app=$k8s/cluster-init/templates/apps/invoice-watcher.yml
ns_prefix=$(value "$argocd_app" "^ *namespace: *'([a-z0-9-]*)\{\{.*\.Values\.environment.*")
for env_dir in "$apps"/environments/*/; do
  env=$(basename "$env_dir")
  helm_env=$k8s/environments/$env.yml.gotmpl
  tofu_root=$infra/environments/$env/ephemeral/main.tf
  environment=$(value "$helm_env" '^environment: *([^[:space:]]+).*')
  same "namespace of $env" "$tofu_root" "$(value "$tofu_root" '^ *app_namespace *= *"([^"]+)".*')" \
    "${env_dir}kustomization.yml" "$(value "${env_dir}kustomization.yml" '^namespace: *([^[:space:]]+).*')" \
    "$argocd_app (environment from $helm_env)" "${ns_prefix:+${environment:+$ns_prefix$environment}}"
done

echo "--- secret-sync-sa"
secret_sync_sa=$k8s/cluster-init/templates/secrets/secret-sync-sa.yml
helmfile=$k8s/helmfile.yaml.gotmpl
places=()
while read -r file; do
  places+=("$file" "$(value "$file" '^ *serviceAccountName: *([^[:space:]]+).*')")
done < <(grep -rl 'serviceAccountName:' "$k8s/cluster-init/templates" | grep -vx "$secret_sync_sa" | sort)
same "secret sync ServiceAccount" "$identity" "$(value "$identity" '.*subject\/ns\/[a-z0-9-]+\/sa\/([a-z0-9-]+)".*')" \
  "$secret_sync_sa" "$(value "$secret_sync_sa" '^  name: *([^[:space:]]+).*')" \
  "${places[@]}"
same "secret sync ServiceAccount namespace" "$identity" "$(value "$identity" '.*subject\/ns\/([a-z0-9-]+)\/sa\/.*')" \
  "$helmfile (cluster-init release)" \
  "$(value "$helmfile" '^ *namespace: *([^[:space:]]+).*' '/- name: cluster-init$/,/namespace:/')"

echo "--- app-sa"
app_sa=$apps/components/app-identity/service-account.yml
app_identity=$apps/components/app-identity/kustomization.yml
same "application ServiceAccount" "$identity" \
  "$(value "$identity" '.*subject\/ns\/\$\{var\.app_namespace\}\/sa\/([a-z0-9-]+)".*')" \
  "$app_sa" "$(value "$app_sa" '^  name: *([^[:space:]]+).*')" \
  "$app_identity" "$(value "$app_identity" '^ *value: *([^[:space:]]+).*' '/serviceAccountName/,/value:/')"

echo "--- gke-gateway-ip"
cluster=$infra/modules/gke/cluster.tf
gateway=$k8s/cluster-init/templates/gateway/gateway.yml
same "gateway address" "$cluster" \
  "$(value "$cluster" '^ *name *= *"([^"]+)".*' '/"google_compute_global_address"/,/name *=/')" \
  "$gateway" "$(value "$gateway" '^ *value: *([^[:space:]]+).*' '/type: NamedAddress/,/value:/')"

echo "--- kafka-data disk"
disk=$infra/modules/gke/disk.tf
volume=$k8s/cluster-init/templates/storage/kafka-data-volume.yml
kafka=$apps/apps/kafka/base/statefulset.yml
same "Kafka data disk" "$disk" "$(value "$disk" '^ *name *= *"([^"]+)".*' '/"google_compute_disk"/,/name *=/')" \
  "$volume (volumeHandle)" "$(value "$volume" '^ *volumeHandle:.*\/disks\/([a-z0-9-]+).*')"
same "Kafka data volume" "$volume" "$(value "$volume" '^  name: *([^[:space:]]+).*')" \
  "$kafka (volumeName)" "$(value "$kafka" '^ *volumeName: *([^[:space:]]+).*')"
same "Kafka data claim" "$volume (claimRef)" "$(value "$volume" '^ *name: *([^[:space:]]+).*' '/claimRef:/,/name:/')" \
  "$kafka (<template>-<statefulset>-0)" \
  "$(value "$kafka" '^ *name: *([^[:space:]]+).*' '/volumeClaimTemplates:/,/name:/')-$(value "$kafka" '^  name: *([^[:space:]]+).*')-0"

((failures == 0)) || exit 1
