#!/bin/bash

BASE_DIR="$(dirname "${BASH_SOURCE[0]}")"
ENV="$1"

if [ -z "$1" ]; then
  echo "Użycie: $0 <environment>, np. $0 dev"
  exit 1
fi

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

for dir in "$BASE_DIR"/*; do
  if [ -d "$dir" ]; then
    apply "$(basename "$dir")"
  fi
done
