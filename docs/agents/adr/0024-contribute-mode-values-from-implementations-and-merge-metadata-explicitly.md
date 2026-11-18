---
id: ADR-0024
status: accepted
date: 2026-11-17
---

# ADR-0024: Contribute mode values from implementations and merge metadata explicitly

## Context

A mode enum in a shared API makes every new backend an API change. Repeated Spring configuration
metadata names do not extend a Java enum, and different tools need not combine duplicated
property/value declarations in the same way. Metadata generation does not require runtime beans.

## Decision

- Declare each selector once as a string property in its capability API. Keep the API passive:
  it declares contracts and metadata, not selector auto-configuration or global validation beans.
- Let implementations own their selection conditions. Generate mode value hints from compiled
  `@ConditionalOnProperty` declarations, without initializing their classes. Do not repeat the
  same value in hand-maintained JSON or in a central supported-backend list.
- Explicitly merge implementation contributions for application metadata and generated reference
  documentation. Union/deduplicate equal hints deterministically; fail incompatible duplicate
  types, defaults, descriptions or conflicting value hints rather than choosing by input order.
  Application metadata includes the runtime classpath's implementations; repository reference
  generation sees the repository's implementation modules.
- Keep completion hints independent of runtime wiring. Active features require service interfaces
  through ordinary dependency injection; selected implementations validate their own properties.
  Test activation, disabled graphs and missing/ambiguous dependencies in implementation and
  application composition tests. Do not introduce a runtime validation framework for selectors.
  A bare API or disabled feature creates no selector/validation infrastructure.
- Declare metadata classpaths as build inputs and regenerate application metadata from current
  inputs, so adding/removing a backend updates suggestions without stale values or an API edit.

## Consequences

New implementations extend supported values through their own code and dependencies. Consumers
fail startup through required dependency wiring when a selected backend is missing or ambiguous,
while API-only usage creates no beans. The build owns explicit aggregation and tests for conflicts/removal rather
than relying on an IDE's duplicate-metadata behavior. String selectors lose compile-time enum
exhaustiveness; conditional activation and required injection enforce runtime availability. Errors
identify the missing or ambiguous interface rather than guaranteeing a selector-specific message,
and some clients may initialize before an unrelated wiring error is discovered.
