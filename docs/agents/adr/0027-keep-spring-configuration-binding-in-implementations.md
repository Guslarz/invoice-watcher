---
id: ADR-0027
status: accepted
date: 2026-11-23
---

# ADR-0027: Keep Spring configuration binding in implementations

## Context

ADR-0003 places configuration properties in API modules, and ADR-0024 uses that placement for
selector declarations. This exposes Spring Boot binding types even when only the implementation
uses them. For example, the Google OAuth service and auto-configuration consume
`GoogleOauthProperties`; callers of the service do not need that binding record.

## Decision

- Keep consumer-facing service interfaces, domain records and exceptions in API modules. Expose
  configuration abstractions only with an explicit consumer-facing need, independently of Spring
  Boot binding and wiring.
- Put `@ConfigurationProperties` records, their constraint dependencies, binding and validation
  in the owning implementation module. Keep the guarded auto-configuration responsible for
  activation and the shared Spring build convention responsible for metadata generation.
- Preserve external property names as supported configuration contracts independently of the
  Java binding class's location. When implementations share a selector key, expose its single
  framework-neutral constant through the capability API without exposing a binding record.
  Keep implementation selection values and generated hints owned by implementations.
- Amend only binding ownership in ADR-0003 and ADR-0024. Preserve library boundaries, explicit
  metadata merging and conditional activation. Migrate existing binding records through scoped
  refactors without changing property names, defaults, validation or runtime behavior.

## Consequences

API consumers no longer need implementation binding types as part of their contracts. Refactors
must update imports, reflection/discovery references, dependency declarations and metadata checks.
The existing API binding records remain migration work; this convention change does not move
them. Moving a binding record alone does not prove that IntelliJ resolves its properties, so IDE
behavior requires separate observation.
