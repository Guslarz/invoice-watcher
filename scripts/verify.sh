#!/usr/bin/env bash
# Single verification entry point: documentation checks, documentation site build, Gradle build.
set -euo pipefail

cd "$(dirname "$0")/.."

branch=$(git branch --show-current)
if [[ "$branch" == master ]]; then
  echo "WARN: you are on master — changes must go through a branch and a pull request (ADR-0002)" >&2
fi

echo "==> Documentation structure"
scripts/check-docs.sh

echo "==> Documentation site"
if command -v mkdocs >/dev/null 2>&1; then
  mkdocs build --strict --quiet --site-dir "$(mktemp -d)"
else
  echo "SKIP: mkdocs not installed (pip install -r requirements-docs.txt)" >&2
fi

echo "==> Gradle build"
./gradlew build analyze
