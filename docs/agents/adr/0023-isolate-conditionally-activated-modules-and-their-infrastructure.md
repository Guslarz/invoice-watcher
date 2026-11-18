---
id: ADR-0023
status: accepted
date: 2026-11-17
---

# ADR-0023: Isolate conditionally activated modules and their infrastructure

## Context

A condition on a library's own configuration does not disable auto-configuration independently
registered by a transitive starter. A module can appear disabled while still creating a database
client, credentials provider, executor or registry. Reusing another capability's unnamed client
creates an equally hidden dependency on its activation and bean names.

## Decision

- Make each conditional implementation own its complete named service/client graph. Guard its
  properties, imports, helper configurations and resources with the same activation condition.
- Do not require a sibling implementation's beans. Use explicitly imported helpers inside the
  selected configuration when sharing construction logic, rather than self-activating helpers.
- Prefer the underlying SDK/library over starters that independently create capability-specific
  infrastructure. Where framework integration must be activated explicitly, keep it within the
  implementation's conditional boundary; do not solve a library problem with app-wide exclusions.
- Keep shared event/data contracts free of runtime activation. Importing an envelope must not
  enable or require an unrelated producer's mode configuration.
- Keep APIs free of runtime selector beans. Active features use required dependency injection
  for their service interfaces; do not silently omit an active feature when its backend is absent.
  Do not add a generic runtime selection validator. Missing or ambiguous dependencies fail normal
  application wiring; selected implementations validate their own bound properties. No guarantee
  is made that every wiring error precedes all client initialization. Selector extensibility and
  build-time metadata contributions follow ADR-0024.
- Test actual auto-configuration discovery and disabled/active/coexisting modes, including
  dependency-supplied configurations, not only manually selected configuration classes.

## Consequences

A module's disabled state becomes predictable, and active implementations can coexist without
borrowing clients. Explicit wiring requires more code and targeted composition tests. Selected backends explicitly own naming,
transaction and connection defaults previously provided by framework auto-configuration, so
removing hidden activation does not change persisted schemas or active backend behavior.
