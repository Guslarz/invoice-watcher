---
id: ADR-0022
status: accepted
date: 2026-11-17
---

# ADR-0022: Use Lombok for boilerplate and named builders for complex records

## Context

Lombok is already provided by the JVM conventions. Long positional constructors make lifecycle
updates hard to review, especially when adjacent arguments have the same type. Handwritten
accessors and utility constructors repeat mechanical code and obscure the meaningful logic.

## Decision

- Prefer records for immutable data. Add `@Builder(toBuilder = true)` when named fields make
  construction easier to understand, especially for large records or similar adjacent parameters.
  Use `toBuilder()` for small changes to an existing value instead of copying every field.
- Use `@RequiredArgsConstructor` for straightforward field injection/construction and `@Getter`
  for mechanical accessors, respecting the repository's fluent-accessor configuration.
- Use `@UtilityClass` for constant holders and static utilities. When only constructor access
  needs changing on another class, use `@NoArgsConstructor(access = PRIVATE)` where appropriate.
- Keep constructors that perform meaningful validation, defensive copying, derived initialization
  or a required superclass call. Generated code is not a reason to move those guarantees elsewhere.
- Keep deliberate diagnostic redaction: do not replace a safe `toString()` with one that renders
  sensitive record fields. Generated builders still pass through record validation and copying.
- Prefer top-level API types when multiple consumers reuse them. Nest types that belong solely
  to their owner, such as a properties record's selector enum, constants and defaults.

## Consequences

Named updates show the fields that actually change. The code depends on the existing annotation
processing setup, and reviewers must know the annotations' generated behavior. Small constructors
can remain clearer than builders; apply the rule for readability rather than annotating every
possible declaration. Data validation, immutability and safe diagnostics remain explicit obligations.
