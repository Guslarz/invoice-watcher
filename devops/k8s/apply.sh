#!/bin/bash

BASE_DIR="$(dirname "${BASH_SOURCE[0]}")"
ENV="$1"
PRIORITY_DIRS=("core")

function apply() {
  name="$1"
  kustomize_dir="$BASE_DIR/$name/overlays/$ENV"

  if [ ! -d "$kustomize_dir" ]; then
      echo "Kustomize directory not found: $kustomize_dir"
      return
  fi

  echo "Applying Kustomize configuration for $name in environment $ENV..."
  kubectl apply -k "$kustomize_dir"
}

for dir in "${PRIORITY_DIRS[@]}"; do
  apply "$dir"
done

for dir in "$BASE_DIR"/*; do
  if [ -d "$dir" ] && [[ ! " ${PRIORITY_DIRS[@]} " =~ " $(basename "$dir") " ]]; then
    apply "$(basename "$dir")"
  fi
done
