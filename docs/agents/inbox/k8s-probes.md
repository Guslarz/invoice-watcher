---
title: Liveness and readiness probes
kind: ops
area: deployment
added: 2026-10-13
---

Actuator health probes are enabled in `mail-producer-app` (`management.endpoint.health.probes.enabled`) but the k8s manifests do not use them; Kafka has no probes either.
