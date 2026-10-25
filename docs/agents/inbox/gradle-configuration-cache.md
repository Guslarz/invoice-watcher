---
title: Make the build configuration-cache compatible
kind: idea
area: build
added: 2026-10-24
---

The configuration cache is not enabled (no project `gradle.properties`). Obstacles:
- `build.gradle.kts:5-8` sets `allprojects { version = rootProject.scmVersion.version }` —
  cross-project configuration that resolves the git version eagerly.
- `build.gradle.kts:21` hardcodes `dependsOn(":apps:secret-helper-app:cliReference")`;
  `:27-48` walks `subprojects` in `gradle.projectsEvaluated` and uses `tasks.findByName("classes")`.
- Verify: `build.gradle.kts:23` reads the script-level `docsModules` inside `doLast`, which usually
  fails configuration-cache serialization.
- `apps/secret-helper-app/build.gradle.kts:35-38` writes into the root build directory and sets
  `standardOutput = ...outputStream()` without closing it.

Run `./gradlew build --configuration-cache` to get the full list of problems.
