---
title: Consider renaming subdirectories in deployments/k8s-apps
kind: tech-debt
area: deployment
added: 2026-10-13
---

Consider renaming the subdirectories of `deployments/k8s-apps` (today: `apps/<app>/{base,overlays/<env>}`,
`components/app-identity`, `environments/<env>`) to clearer names. Nothing is broken; the current
names were just not chosen deliberately.

A rename has to update every reference to the paths:
- the ArgoCD application in `deployments/k8s/cluster-init/templates/apps/invoice-watcher.yml`,
  which points at `deployments/k8s-apps/environments/<env>`,
- relative paths in the kustomizations themselves,
- `docs/agents/guidelines/deployments.md`, `docs/specs/deployment.md` (DEPLOY-030, DEPLOY-032),
  ADR-0004 and the repository map in `AGENTS.md`.
