---
title: Resolve open Dependabot alerts
kind: tech-debt
area: build
added: 2026-10-13
---

On 2026-10-13 GitHub reported 37 open Dependabot alerts on `master` (7 critical, 15 high,
14 medium, 1 low). All are Maven dependencies from the graph submitted by
`.github/workflows/dependency-submission.yml` (manifest `settings.gradle.kts`), mostly
transitive ones managed by the Spring Boot / spring-cloud-gcp BOMs:

- critical: `tomcat-embed-core`, `bcprov-jdk18on`, `spring-webmvc`
- high: `jackson-databind` / `jackson-core` (both `tools.jackson` and `com.fasterxml`),
  `bcprov` / `bcpg-jdk18on`, `lz4-java`, `httpcore5` / `httpcore5-h2`, `plexus-utils`,
  `postgresql`
- medium/low: `lz4-java`, `jackson-databind`, `commons-lang3`, `httpclient5`, `log4j-api`,
  `bcpkix-jdk18on`, `bcprov-jdk18on`, `kotlin-gradle-plugin`

Approach: first bump the Spring Boot plugin and `spring-cloud-gcp-dependencies` BOM
(`gradle/libs.versions.toml`, `buildSrc/.../spring-conventions.gradle.kts`) and the build plugins
(`kotlin-gradle-plugin`, `plexus-utils` come from the build classpath), then check what remains
with `./gradlew dependencyInsight` and override only those. Consider enabling Dependabot version
updates (`.github/dependabot.yml`) so this does not pile up. Current list:
https://github.com/Guslarz/invoice-watcher/security/dependabot
