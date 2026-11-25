---
title: Audit and move Spring configuration binding into implementations
kind: tech-debt
area: build
added: 2026-11-23
status: ready
size: L
blocked-by: []
---

Audit configuration binding across the repository against
[ADR-0027](../adr/0027-keep-spring-configuration-binding-in-implementations.md). Search all modules
for `@ConfigurationProperties` declarations and `@EnableConfigurationProperties` registrations,
then trace consumers, shared selector constants and binding-related dependencies to identify
nonconforming API classes. Include `GoogleOauthProperties` as a known candidate; determine the
remaining migration scope from the search rather than assuming Google OAuth is the only case.

For each candidate, distinguish implementation binding details from abstractions that consumers
actually need. Move binding records into the owning implementation's `properties` package;
retain a framework-neutral API abstraction or shared selector key only with an explicit
consumer-facing reason. Update imports, tests, reflection/discovery references and dependency
declarations, removing API dependencies used only for binding where no other API type requires
them. Preserve external property names, defaults, validation, guarded activation and runtime
secret types. Verify generated and packaged metadata as well as binding and activation behavior.

Use the audit to plan scoped migrations; split independent areas into follow-up notes or an
initiative if several pull requests are needed. Record justified API abstractions and remaining
migration work so partial implementation does not close the repository-wide audit prematurely.

Preserve the confirmed IntelliJ resolution supplied by source scalar metadata under
[ADR-0028](../adr/0028-describe-secret-scalars-in-source-and-packaged-metadata.md). Run the targeted
IDE regression check after moving the binding records and their metadata resources.
