---
title: Clean up the convention plugins in buildSrc
kind: tech-debt
area: build
added: 2026-10-24
---

`buildSrc/src/main/kotlin/`:
- Three plugin ID styles: unpackaged `java-*-conventions` / `spring-boot-*-conventions`,
  `dependencies.lombok-conventions` / `dependencies.spring-conventions`, `docker.image-conventions`.
  Lombok is a compiler plugin, not a "dependency".
- Thin wrappers: `java-app-conventions` only applies `java-common-conventions`;
  `lombok-conventions` only applies `io.freefair.lombok`; `java-lib-conventions` is used only by
  `spring-boot-lib-conventions` although the guideline offers it to modules.
- Duplicated blocks in both spring conventions: `withType<Test> { useJUnitPlatform() }`,
  `spring-boot-configuration-processor`, `spring-boot-starter-test` — they belong in
  `java-common-conventions`. `withType<Test> { }` is eager; use `.configureEach { }`.
- `spring-boot-lib-conventions.gradle.kts:4` declares `id("org.springframework.boot") apply false`
  but `:22-24` configures `named("bootJar")`, and the libraries need the Boot BOM — the `apply false`
  is misleading. Libraries could use `platform(SpringBootPlugin.BOM_COORDINATES)` instead.
- `spring-boot-lib-conventions.gradle.kts:18-20` adds `processResources` as an input of
  `compileJava` without a comment; no `additional-spring-configuration-metadata.json` exists, so
  it may be stale.
- The Java version is set via `sourceCompatibility` in `java-common-conventions` and
  `buildSrc/build.gradle.kts`, not with a toolchain; repositories are declared per project instead
  of in `dependencyResolutionManagement`.
- `analyze` only `dependsOn("check")` and `build` depends on `analyze`, so `./gradlew build analyze`
  (`scripts/verify.sh`, CI) runs nothing extra, and `analyze` also runs the tests — the
  guidelines describe it as Checkstyle + SpotBugs only.
