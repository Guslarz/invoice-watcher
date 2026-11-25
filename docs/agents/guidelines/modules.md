# Modules and Spring auto-configuration

## Layout

- Modules are discovered automatically by the `settings.module-discovery` settings plugin
  (`gradle/build-logic/settings`): every directory under `apps/`, `images/`, `libs/` containing a
  `build.gradle.kts` becomes a project. Path
  `libs/dao/user-configuration/api` → project `:libs:dao-user-configuration-api` → type-safe accessor
  `projects.libs.daoUserConfigurationApi`. Never edit `settings.gradle.kts` to add a module.
- Every `build.gradle.kts` sets a one-line `description`; it is published in the generated module
  reference, and a module without one fails the docs build.
- A library area has an `api` module and one or more implementations:
  - `api` — consumer-facing service interfaces, domain records and exceptions. Expose a
    configuration abstraction only when consumers need it, with an explicit reason; keep that
    abstraction independent of Spring Boot binding and wiring. No Spring beans, binding records
    or auto-configuration.
  - `impl` / `<tech>-impl` — implementation, Spring Boot configuration binding records and their
    validation, and explicitly guarded `@AutoConfiguration`. Implementations own their conditional
    selection values; adding a backend must not require an API change.
  External property names remain supported configuration contracts regardless of the binding
  record's Java package. See [ADR-0027](../adr/0027-keep-spring-configuration-binding-in-implementations.md).
- Consumers depend on `api` (`api(projects.libs.xApi)`); only applications depend on `impl`.
  An `api` module is always declared with `api(...)`, never `implementation`/`compileOnly`; leave
  it out when another declared `api` module already brings it.
- Applications (`apps/*`) contain almost no code — they compose `impl` modules in
  `build.gradle.kts`.
- OAuth provider libraries live under `libs/oauth/<provider>/{api,impl}`, with packages
  `com.guslarz.invoicewatcher.oauth.<provider>.{api,impl}`. For Google, the discovered projects
  are `:libs:oauth-google-api` and `:libs:oauth-google-impl`, accessed as
  `projects.libs.oauthGoogleApi` and `projects.libs.oauthGoogleImpl`.
  Each provider binds independent `invoice-watcher.oauth.<provider>` settings and activates
  independently. Providers may coexist; do not introduce an exclusive provider `mode` or let
  one provider's bean suppress another provider. Name provider beans distinctly where needed.

## Package roles

- Within a module's package root, group types by responsibility: `service` for contracts and
  implementations, `model` for operational/transport values, `exception` for exceptions and
  `properties` for settings. Reserve `entity` for persisted state and persistence representations.
- Put Spring wiring in `configuration`; use `mapper` and `repository` where those roles exist.
  APIs and implementations use the same vocabulary without requiring identical or empty packages.
  Keep small internal service helpers alongside their service and tests in corresponding packages.
- Class moves must update imports, discovery registrations and reflective references while
  preserving property names, wire contracts and persisted schemas. See
  [ADR-0025](../adr/0025-organize-java-library-packages-by-role.md).

## Auto-configuration

- Every `impl` registers its auto-configuration in
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Beans are added with `@Import({...})` of `@Component` classes, not component scanning.
- Alternative implementations are switched by a string `mode` property, e.g.
  `invoice-watcher.user-configuration.dao.mode = jpa | firestore`,
  `invoice-watcher.event-producer.mode = kafka`, guarded with `@ConditionalOnProperty` using the
  constants owned by the configuration group. When implementations share a selector key, declare
  it once as a framework-neutral API constant; sharing the key does not expose a Spring binding
  record. Do not use closed API enums for implementation selection.
- Keep every implementation's complete client/provider/helper graph behind its activation
  condition. Use underlying libraries rather than starters that independently activate provider
  infrastructure; do not depend on a sibling implementation's unnamed/default beans. Shared
  helpers must be imported within the active condition, not globally self-activated. See
  [ADR-0023](../adr/0023-isolate-conditionally-activated-modules-and-their-infrastructure.md).
- Active features require service interfaces through ordinary dependency injection. Do not hide
  missing backends with conditions that silently omit the active feature, or add generic runtime
  selection validators. Test disabled/selected implementations and application compositions,
  including absent and ambiguous dependencies. API-only imports create no selector beans.
- Keep backend activation/isolation tests in the owning implementation module. Start a minimal
  test application with normal Spring Boot auto-configuration discovery and the module's
  transitive dependencies; do not filter discovery to selected configuration classes. Test
  consuming capabilities' required dependencies in their own modules, and reserve application
  tests for actual application composition.
- When several implementations may live on one classpath, name beans explicitly (nested `Beans`
  utility class with string constants) and wire them with `@Qualifier`, so they never clash with
  Spring Boot's own auto-configured beans.

## Configuration properties

- `@ConfigurationProperties` **records**, prefix `invoice-watcher.<area>[.<sub>]`.
- Put binding records in the owning implementation's `properties` package, with binding and
  validation enabled by that implementation's guarded auto-configuration. Declare Spring Boot
  and constraint dependencies in that module; do not add them to an API for configuration binding.
  Keep metadata generation in the shared Spring convention. Migrate existing API binding records
  through scoped refactors, preserving property names, defaults, validation, activation and
  generated/packaged metadata; moving a Java type does not change the external configuration contract.
- Derive child configuration prefixes from the owning group's prefix constant plus a suffix,
  and reuse selector property constants in activation conditions.
- Defaults via `@DefaultValue` with constants in a nested `Defaults` class.
- Bound records are validated at startup: `@Validated` on the record, `jakarta.validation`
  constraints on the components (`@NotBlank`, `@NotNull`, `@DurationMin`, ...), `@Valid` on nested
  records. A record bound only in one mode is enabled only in that mode, so its constraints apply
  only there.
- Every module that binds a record (`@EnableConfigurationProperties`) declares
  `implementation(libs.spring.boot.starter.validation)` itself; without a validator on the
  classpath Spring silently ignores `@Validated`. Completion hints are build-time metadata; required
  dependency injection rejects missing or ambiguous backends when a capability is active.
- The configuration processor (enabled in `springboot.common-conventions`) generates metadata
  that feeds the published configuration reference. Every record (also nested ones) needs Javadoc
  with a `@param` per component — it becomes the property description, and a property without
  one fails the docs build (`scripts/gen-reference.py`). Every description starts with
  "Required." or "Optional.", with the condition where it applies (e.g. "Required in `jpa` mode.").
- Declare each `Secret` property as `java.lang.String` in its module's source resource
  `META-INF/additional-spring-configuration-metadata.json`, matching its encrypted scalar input.
  IntelliJ's project-source resolver reads that file; generated JAR metadata alone does not fix
  source inference. The shared Spring convention tracks these files as compiler inputs and
  normalizes compiled `Secret` property types, rejecting a missing source scalar declaration.
  Keep Java property types and runtime converters unchanged. See
  [ADR-0028](../adr/0028-describe-secret-scalars-in-source-and-packaged-metadata.md).
- In k8s, properties are set as env vars in `overlays/<env>/config/.env` of the app
  (`invoice-watcher.mail-producer.task.topic` → `INVOICE_WATCHER_MAIL_PRODUCER_TASK_TOPIC`).

## Implementation metadata

The Gradle convention generates mode value hints from each implementation's compiled
`@ConditionalOnProperty` declaration. Keep the condition as the single source of the value;
do not repeat an implementation list in the API or hand-maintain equivalent JSON hints.
Application metadata and the generated reference merge these contributions deterministically,
deduplicate equal values and fail on incompatible duplicate declarations. The application view
contains only implementations on its classpath; the reference documents the repository's modules.
See [ADR-0024](../adr/0024-contribute-mode-values-from-implementations-and-merge-metadata-explicitly.md).
