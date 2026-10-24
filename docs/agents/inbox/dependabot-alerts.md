---
title: Resolve Dependabot alerts that Dependabot cannot fix
kind: tech-debt
area: build
added: 2026-10-24
---

On 2026-10-24 GitHub reports 37 open Dependabot alerts on `master`
(https://github.com/Guslarz/invoice-watcher/security/dependabot). Checked against the submitted
dependency graph (`gh api repos/Guslarz/invoice-watcher/dependency-graph/sbom`):

**16 stale** — the graph already contains the fixed version, but the alerts were never closed
(`updated_at` = `created_at`). Cause unknown, to be investigated: `spring-webmvc` 7.0.9,
`log4j-api` 2.25.5, `postgresql` 42.7.13, `httpcore5` / `httpcore5-h2` 5.4.3, `httpclient5` 5.6.4,
`bcprov` / `bcpg` / `bcpkix-jdk18on` 1.85–1.86, `tools.jackson` `jackson-databind` (alerts fixed
in 3.1.5).

**21 real** — the vulnerable version is still in the graph:
- `tomcat-embed-core` 11.0.24 → fixed in 11.0.25
- `tools.jackson` `jackson-core` / `jackson-databind` 3.1.5 → 3.1.7
- `com.fasterxml` `jackson-core` 2.21.5 → 2.21.7
- `lz4-java` 1.10.1 → 1.11.4
- `commons-lang3` 3.16.0 (next to 3.20.0, probably the build classpath) → 3.18.0
- `plexus-utils` 3.3.0 (build classpath) → 3.6.1
- `kotlin-gradle-plugin` 2.4.10 (`buildSrc` `kotlin-dsl`) → only 2.4.20-Beta1

Dependabot (Gradle version updates and security updates are enabled) cannot fix these: the
versions come from the Spring Boot / Spring Cloud GCP BOMs or from the build classpath, not from
files it edits. Spring Boot 4.1.1 still pins Tomcat 11.0.24 and Jackson 3.1.5.

Decision: wait for BOM (and Kotlin) releases with the fixes, which Dependabot will bump; no
version overrides for now. Re-check the list after each Spring Boot / Spring Cloud GCP bump. If a
release with the fix does not come, override only the affected versions (e.g. a Gradle
constraint or BOM property, checked with `./gradlew dependencyInsight`) and remove the override
once the BOM catches up.
