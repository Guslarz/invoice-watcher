---
title: Move hardcoded versions into the version catalog
kind: tech-debt
area: build
added: 2026-10-24
---

The build guideline says versions live in `gradle/libs.versions.toml`, but:
- `dependencies/spring-conventions.gradle.kts:9`:
  `mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:8.0.4") // Can't pass it any other way`
  — precompiled script plugins can read the catalog via
  `the<VersionCatalogsExtension>().named("libs")`.
- `java-code-analysis-conventions.gradle.kts:7`: Checkstyle `toolVersion = "11.0.0"`.
- The conventions use string coordinates for `spring-boot-starter` and `spring-boot-starter-test`
  while catalog entries exist; `spring-boot-configuration-processor` has no entry.
- Unused catalog entries: `spring-boot-starter-test` (because of the string above),
  `spring-cloud-gcp-starter-firestore` (only `-datastore` is used).
- The catalog has no `[versions]` section, so related artifacts cannot share one version.

Related: `dependabot-alerts.md` (BOM bump).
