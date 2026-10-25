---
title: Enable Isolated Projects
kind: tech-debt
area: build
added: 2026-10-25
---

The build's own logic no longer reads other projects (configuration cache is on), but
`-Dorg.gradle.unsafe.isolated-projects=true` still fails, as of Gradle 9.8.2:
- `pl.allegro.tech.build.axion-release` 1.21.4 calls `allprojects` itself when applied, and the root
  `build.gradle.kts` sets `allprojects { version = rootProject.scmVersion.version }`. Axion supports
  only root-applied (cross-project) or per-module use; per-module would give every project a
  `release` task.
- `io.spring.dependency-management` and Spring Boot's `BootJar` (`ResolvedDependencies`) read
  `Project.group` of other projects. Replacing the dependency-management plugin with Gradle
  platforms (`platform(...)`) would remove the first of them.

Recheck after upgrading these plugins: `./gradlew build -Dorg.gradle.unsafe.isolated-projects=true`.
