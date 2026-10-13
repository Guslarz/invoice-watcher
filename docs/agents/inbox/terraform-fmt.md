---
title: Run terraform fmt and enforce it in CI
kind: tech-debt
area: deployment
added: 2026-10-13
---

Format `deployments/infrastructure/**` and add a `terraform fmt -check` step to CI.
