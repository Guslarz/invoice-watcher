#!/usr/bin/env bash
# Single verification entry point: documentation checks, generated reference, deployment files,
# documentation site build, Gradle build. The regenerated docs/reference/ pages are part of the change - commit them.
set -euo pipefail

cd "$(dirname "$0")/.."

branch=$(git branch --show-current)
if [[ "$branch" == master ]]; then
  echo "WARN: you are on master — changes must go through a branch and a pull request (ADR-0002)" >&2
fi

echo "==> Documentation structure"
scripts/check-docs.sh

echo "==> Generated reference"
./gradlew --quiet docsReference
python3 scripts/gen-reference.py

echo "==> Overlay environment variables"
# Every INVOICE_WATCHER_* variable in a kustomize .env must bind, by Spring Boot relaxed binding,
# to a property documented in the generated reference: '.' -> '_', '-' removed or -> '_',
# upper case. List indexes ('_0') are ignored.
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

echo "==> Deployment files"
scripts/check-deployments.sh

echo "==> Documentation site"
if command -v mkdocs >/dev/null 2>&1; then
  mkdocs build --strict --quiet --site-dir "$(mktemp -d)"
else
  echo "SKIP: mkdocs not installed (pip install -r requirements-docs.txt)" >&2
fi

echo "==> Gradle build"
./gradlew build analyze
