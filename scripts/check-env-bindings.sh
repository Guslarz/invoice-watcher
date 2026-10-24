#!/usr/bin/env bash
# Every INVOICE_WATCHER_* variable in a kustomize .env must bind, by Spring Boot relaxed binding,
# to a property documented in the generated reference: '.' -> '_', '-' removed or -> '_',
# upper case. List indexes ('_0') are ignored. Run by CI (Build and analyze / reference) and
# scripts/verify.sh, after docs/reference/ is generated.
set -euo pipefail

cd "$(dirname "$0")/.."

declare -A bindable=()
while read -r property; do
  bindable[$(tr 'a-z.' 'A-Z_' <<<"${property//-/}")]=1
  bindable[$(tr 'a-z.-' 'A-Z__' <<<"$property")]=1
done < <(grep -oE '^\| `invoice-watcher\.[a-z0-9.-]+`' docs/reference/configuration.md | tr -d '|` ')
unknown=0
while IFS=: read -r file key; do
  if [[ -z "${bindable[$(sed -E 's/_[0-9]+(_|$)/\1/g' <<<"$key")]:-}" ]]; then
    echo "FAIL: $file: $key does not bind to any property in docs/reference/configuration.md" >&2
    unknown=$((unknown + 1))
  fi
done < <(grep -oE '^INVOICE_WATCHER_[A-Z0-9_]+' --with-filename -r --include=.env deployments/k8s-apps)
((unknown == 0)) || exit 1
