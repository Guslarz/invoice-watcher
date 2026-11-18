---
id: ADR-0025
status: accepted
date: 2026-11-18
---

# ADR-0025: Organize Java library packages by role

## Context

The processor and processing DAO APIs contain service contracts, data records, exceptions and
configuration properties in flat packages. Their implementation roots similarly mix startup
wiring with runtime services. As these capabilities grow, the flat layout obscures responsibilities
and makes corresponding contracts and implementations harder to locate.

## Decision

- Keep the module's capability and implementation identity in its package root, then group Java
  types by role: `service` for service interfaces and implementations, `model` for operational
  and transport contracts, `exception` for exceptions, and `properties` for bound settings.
- Reserve `entity` for persisted state or persistence representations, including durable snapshots
  exposed through a DAO contract. Do not classify every record returned by a service as an entity.
- Put Spring auto-configuration and wiring under `configuration`. Use backend-specific `entity`,
  `mapper` or `repository` packages where those responsibilities exist. Keep tightly coupled
  service helpers with their service when they do not warrant a separate role package.
- Use corresponding role names in APIs and implementations, without forcing identical layouts,
  creating empty packages or splitting types merely for symmetry. Name public top-level types for
  their domain and operation, such as `ProcessingOutcome`, rather than relying on the package to
  explain a generic name such as `Outcome`. Keep a type's constants and
  defaults nested when they belong exclusively to that type.
- Mirror the relevant production packages in tests. Keep internal helpers package-private; make
  implementation types accessible across role packages only where explicit wiring requires it.
- Apply this layout to the new processor and processing DAO modules in this change. Migrate
  existing modules through scoped refactors tracked in the naming-conventions inbox note.

## Consequences

Package names communicate responsibilities and distinguish persisted state from operation models.
Imports and Spring discovery registrations must follow class moves. Moving a class must not change
configuration property names, event payloads, persisted field names or storage kinds. Future
migrations must check reflective references, serialization and configuration-relative discovery,
not only compile Java imports. The convention adds package boundaries while preserving the existing
module API/implementation dependency rules.
