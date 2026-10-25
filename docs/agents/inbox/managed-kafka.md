---
title: Managed Kafka for a production environment
kind: idea
area: deployment
added: 2026-10-25
---

A production environment could use Google Cloud Managed Service for Apache Kafka instead of the
single in-cluster broker. Compared when giving dev Kafka a persistent disk:

- Managed Kafka: minimum 3 vCPU / 3 GiB, from $0.09 per vCPU-hour (roughly $200+/month running
  continuously); 20–30 minutes to create a cluster; client auth through `GcpLoginCallbackHandler`
  (SASL/OAUTHBEARER) with workload identity; a VPC subnet for the brokers; topics managed in
  OpenTofu.
- In-cluster Kafka on a 10 GiB `pd-balanced` disk: about $1/month, created and destroyed with the
  environment.

Rejected for the up/down dev environment (cost and creation time); worth revisiting for an
environment that stays up.
