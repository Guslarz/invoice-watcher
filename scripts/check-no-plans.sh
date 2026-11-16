#!/usr/bin/env bash
# Temporary plans may be untracked working material, but must not enter a PR.
set -euo pipefail

cd "$(dirname "$0")/.."

errors=0
while IFS= read -r -d '' path; do
  case "$path" in
    PLAN.md|docs/agents/plans/*.md|docs/agents/projects/*.md)
      case "$path" in
        docs/agents/plans/README.md|docs/agents/projects/README.md) continue ;;
      esac
      printf 'FAIL: temporary plan is tracked: %s; remove it from Git before committing\n' "$path" >&2
      errors=1
      ;;
  esac
done < <(git ls-files -z)

if ((errors)); then
  exit 1
fi
echo "check-no-plans: OK"
