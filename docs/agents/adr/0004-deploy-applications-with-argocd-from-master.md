---
id: ADR-0004
status: accepted
date: 2026-10-13
---

# ADR-0004: Bootstrap the cluster with helmfile and deploy applications with ArgoCD from master

## Context

The cluster is recreated often. Cluster-level services (ArgoCD, cert-manager, external-secrets,
gateway) change rarely; application manifests change with the code.

## Decision

- Cluster services are installed by helmfile (`deployments/k8s`) from the `Environment` workflow
  right after the infrastructure is created.
- Applications are described with kustomize (`deployments/k8s-apps`: base, overlays per
  environment, shared components) and synced by ArgoCD from `master` with prune and self-heal.

## Consequences

- A merged change to `deployments/k8s-apps` is live without running a workflow; manual changes
  in the cluster are reverted.
- What runs is exactly what is on `master` — another reason `master` changes only through pull
  requests (ADR-0002).
