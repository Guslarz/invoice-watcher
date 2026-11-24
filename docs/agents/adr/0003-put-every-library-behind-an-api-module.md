---
id: ADR-0003
status: amended by ADR-0027
date: 2026-10-13
---

# ADR-0003: Put every library behind an api module and wire implementations by auto-configuration

## Context

The mail producer combines several concerns — mail access, storage, event publishing, secrets —
each with one or more possible technologies (Firestore or JPA, Kafka or something else later).
Applications should be assembled from parts without code changes.

## Decision

- Each concern is an `api` module (consumer-facing interfaces, domain records, exceptions) plus
  one or more implementation modules. [ADR-0027](0027-keep-spring-configuration-binding-in-implementations.md)
  amends the original placement of configuration properties in the API: Spring Boot binding
  belongs in implementations.
- Implementations register a Spring Boot `@AutoConfiguration`; applications only list modules in
  `build.gradle.kts`.
- Alternatives that can share a classpath are switched by a `mode` property
  (`...dao.mode`, `...event-producer.mode`) and use explicitly named beans.

## Consequences

- Swapping a technology is a configuration change; adding one is a new module.
- More modules and some ceremony (`AutoConfiguration.imports`, `Constants`, `Beans`) for small
  pieces of code. Details: `docs/agents/guidelines/modules.md` (agent guideline).
