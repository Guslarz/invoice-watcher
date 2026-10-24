---
title: Enforce dependency declarations with the dependency-analysis plugin
kind: idea
area: build
added: 2026-10-24
---

`com.autonomousapps.dependency-analysis` 3.19.2 was tried in `jvm.code-analysis-conventions` (with
`projectHealth` in `analyze` and `severity("fail")`) and dropped, because it did not work without a
configuration of its own. Its defaults reported ~200 issues in all 16 modules, mostly:
- Starters (`spring-boot-starter*`, `spring-cloud-gcp-starter-*`) reported as unused; it wants their
  jars declared one by one (logback, log4j-to-slf4j, assertj, mockito, gax, grpc, ...). Needs a
  `structure { bundle(...) }` per starter.
- `impl` modules of `apps/mail-producer-app` reported as unused — they are loaded through
  `AutoConfiguration.imports`, not referenced; needs excludes (or `runtimeOnly`).
- `annotationProcessor(lombok)` reported as undeclared (added by the freefair plugin), and
  `spring-boot-configuration-processor` as unused in modules without `@ConfigurationProperties`.
- `api(libs.spring.boot)` instead of `implementation` in api modules, and
  `implementation(projects.libs.secretApi)` where an `api` module is used but not exposed; both
  contradict `docs/agents/guidelines/modules.md` (api modules are `api` or not declared) — needs
  excludes or a decision first.
- The default daemon (512 MB heap, 384 MB metaspace) died with `OutOfMemoryError: Metaspace`;
  a project `gradle.properties` with `org.gradle.jvmargs` is needed.

The advice that was clearly right was applied by hand. Adopting the plugin means the bundles,
excludes and the api/implementation rule above, then making it fail `analyze` (RELEASE-001).
