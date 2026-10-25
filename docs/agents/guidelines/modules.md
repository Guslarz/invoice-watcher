# Modules and Spring auto-configuration

## Layout

- Modules are discovered automatically by the `settings.module-discovery` settings plugin
  (`gradle/build-logic/settings`): every directory under `apps/`, `images/`, `libs/` containing a
  `build.gradle.kts` becomes a project. Path
  `libs/mail-producer/dao/api` → project `:libs:mail-producer-dao-api` → type-safe accessor
  `projects.libs.mailProducerDaoApi`. Never edit `settings.gradle.kts` to add a module.
- Every `build.gradle.kts` sets a one-line `description`; it is published in the generated module
  reference, and a module without one fails the docs build.
- A library area has an `api` module and one or more implementations:
  - `api` — interfaces, records, `@ConfigurationProperties`, exceptions. No Spring beans and no
    auto-configuration: the configuration processor generates metadata for properties records
    without registering them, and implementations use only their `Constants`. A module with
    `@ConfigurationProperties` depends on `implementation(libs.spring.boot)` — nothing more.
    The one exception: an `api` module that defines a `mode` selector registers one
    auto-configuration that only binds and validates that record (`<Area>ModeAutoConfiguration`),
    so every implementation — and an application without any — gets the check.
  - `impl` / `<tech>-impl` — implementation plus an `@AutoConfiguration` class.
- Consumers depend on `api` (`api(projects.libs.xApi)`); only applications depend on `impl`.
  An `api` module is always declared with `api(...)`, never `implementation`/`compileOnly`; leave
  it out when another declared `api` module already brings it.
- Applications (`apps/*`) contain almost no code — they compose `impl` modules in
  `build.gradle.kts`.

## Auto-configuration

- Every `impl` registers its auto-configuration in
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Beans are added with `@Import({...})` of `@Component` classes, not component scanning.
- Alternative implementations are switched by a `mode` property defined in the `api` module, e.g.
  `invoice-watcher.mail-producer.dao.mode = jpa | firestore`,
  `invoice-watcher.event-producer.mode = kafka`, guarded with `@ConditionalOnProperty` using the
  constants from the properties' nested `Constants` class.
- When several implementations may live on one classpath, name beans explicitly (nested `Beans`
  utility class with string constants) and wire them with `@Qualifier`, so they never clash with
  Spring Boot's own auto-configured beans.

## Configuration properties

- `@ConfigurationProperties` **records**, prefix `invoice-watcher.<area>[.<sub>]`.
- Defaults via `@DefaultValue` with constants in a nested `Defaults` class.
- Bound records are validated at startup: `@Validated` on the record, `jakarta.validation`
  constraints on the components (`@NotBlank`, `@NotNull`, `@DurationMin`, ...), `@Valid` on nested
  records. A record bound only in one mode is enabled only in that mode, so its constraints apply
  only there.
- Every module that binds a record (`@EnableConfigurationProperties`) declares
  `implementation(libs.spring.boot.starter.validation)` itself; without a validator on the
  classpath Spring silently ignores `@Validated`. A `mode` record is an enum component with
  `@NotNull`, bound by its `api` module, so a missing or unknown value fails the startup even
  when no implementation matches.
- The configuration processor (enabled in `spring.boot-common-conventions`) generates metadata
  that feeds the published configuration reference. Every record (also nested ones) needs Javadoc
  with a `@param` per component — it becomes the property description, and a property without
  one fails the docs build (`scripts/gen-reference.py`). Every description starts with
  "Required." or "Optional.", with the condition where it applies (e.g. "Required in `jpa` mode.").
- In k8s, properties are set as env vars in `overlays/<env>/config/.env` of the app
  (`invoice-watcher.mail-producer.task.topic` → `INVOICE_WATCHER_MAIL_PRODUCER_TASK_TOPIC`).
