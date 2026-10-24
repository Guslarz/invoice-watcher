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
scripts/gen-reference.sh

echo "==> Overlay environment variables"
scripts/check-env-bindings.sh

echo "==> Deployment files"
scripts/check-deployments.sh

echo "==> Documentation site"
# Zensical is installed into the git-ignored .venv-docs/ and reinstalled when requirements-docs.txt
# changes. It builds into the git-ignored site/ (it refuses a site_dir outside the project).
venv=.venv-docs
if [[ ! -x "$venv/bin/zensical" || requirements-docs.txt -nt "$venv/.installed" ]]; then
  rm -rf "$venv"
  if ! python3 -m venv "$venv" >/dev/null 2>&1; then
    rm -rf "$venv"
    if ! { command -v virtualenv >/dev/null 2>&1 && virtualenv --quiet "$venv"; }; then
      echo "FAIL: cannot create $venv - install python3-venv (e.g. sudo apt install python3-venv)" >&2
      exit 1
    fi
  fi
  "$venv/bin/pip" install --quiet --no-cache-dir -r requirements-docs.txt
  touch "$venv/.installed"
fi
"$venv/bin/zensical" build --strict

echo "==> Gradle build"
./gradlew build analyze
