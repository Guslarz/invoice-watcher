---
id: ADR-0020
status: accepted
date: 2026-11-17
---

# ADR-0020: Hide database operations behind DAO APIs

## Context

Applications and domain services need persistence operations without depending on a database
vendor, entity mapping or transaction implementation. ADR-0003 establishes API/implementation
modules for libraries; persistence needs the same boundary across all capabilities.

## Decision

- Put database operations behind capability-owned DAO interfaces and immutable records in an
  API module. Applications and other libraries call those interfaces rather than provider SDKs,
  Spring Data repositories, JPA entities or transaction managers.
- Keep vendor clients, mappings, queries, indexes and transaction handling in the selected DAO
  implementation. Expose semantic atomic operations where correctness requires them; do not
  replace a transaction with independently callable generic saves.
- Keep provider types out of DAO signatures and exported dependencies. Reuse lower-level helpers
  only when they have an actual shared responsibility; do not couple one capability's DAO to
  another capability's auto-configuration.
- Add implementations when needed. A DAO API does not require speculative implementations for
  every database. Backend selection and conditional activation follow the module conventions.

## Consequences

Persistence technology can change without teaching callers its client API. Transaction semantics
remain visible as capability operations while their implementation stays encapsulated. Separate
contracts add deliberate code boundaries; avoid a universal generic repository that loses each
capability's consistency requirements. This decision does not select one database for every stage
or specify how database state is published to Kafka; publication recovery has its own decision.
