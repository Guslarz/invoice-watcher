---
title: Persistent volume for Kafka
kind: ops
area: deployment
added: 2026-10-13
---

Kafka in `deployments/k8s-apps/apps/kafka` runs without a volume, so data is lost on pod restart.
