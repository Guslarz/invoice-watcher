#!/usr/bin/env bash
# Regenerates docs/reference/ from the code, run by CI (Build and analyze / reference) and
# scripts/verify.sh. The regenerated pages are part of the change - commit them.
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew --quiet docsReference
python3 scripts/gen-reference.py
