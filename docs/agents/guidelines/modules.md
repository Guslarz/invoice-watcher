# Modules and Spring auto-configuration

## Layout

- Modules are discovered automatically by `settings.gradle.kts`: every directory under `apps/`,
  `images/`, `libs/` containing a `build.gradle.kts` becomes a project. Path
  `libs/mail-producer/dao/api` → project `:libs:mail-producer-dao-api` → type-safe accessor
  `projects.libs.mailProducerDaoApi`. Never edit `settings.gradle.kts` to add a module.
- Every `build.gradle.kts` sets a one-line `description`; it is published in the generated module
  reference, and a module without one fails the docs build.
- A library area has an `api` module and one or more implementations:
  - `api` — interfaces, records, `@ConfigurationProperties`, exceptions. No Spring beans except an
    optional `@AutoConfiguration` that only enables properties.
  - `impl` / `<tech>-impl` — implementation plus an `@AutoConfiguration` class.
- Consumers depend on `api` (`api(projects.libs.xApi)`); only applications depend on `impl`.
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
- The configuration processor (enabled in `spring-boot-{app,lib}-conventions`) generates metadata
  that feeds the published configuration reference. Every record (also nested ones) needs Javadoc
  with a `@param` per component — it becomes the property description, and a property without
  one fails the docs build (`scripts/gen-reference.py`).
- In k8s, properties are set as env vars in `overlays/<env>/config/.env` of the app
  (`invoice-watcher.mail-producer.task.topic` → `INVOICE_WATCHER_MAIL_PRODUCER_TASK_TOPIC`).
