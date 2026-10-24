# Build and release

- Use the wrapper: `./gradlew build analyze`. `analyze` = Checkstyle + SpotBugs only, without
  tests; `build` runs them too (through `check`). Any Checkstyle violation fails the build
  (`maxWarnings = 0`).
- Convention plugins live in `buildSrc/src/main/kotlin`, grouped by package, so the ID is
  `<package>.<name>-conventions` (`jvm`, `spring`, `docker`; not `java`, which the JVM reserves).
  Module build files only pick one (`spring.boot-app-conventions`, `spring.boot-lib-conventions`,
  `jvm.lib-conventions`, `docker.image-conventions`) and list dependencies. Shared build logic
  goes to `buildSrc`, not to module build files. Precompiled scripts read the catalog with
  `versionCatalogs.named("libs")`.
- Java 25 via toolchain; repositories only in `dependencyResolutionManagement` of
  `settings.gradle.kts` (project repositories fail the build).
- Dependency versions live in `gradle/libs.versions.toml`; Spring-managed ones have no version.
  Spring modules import the `spring-cloud-gcp-dependencies` BOM (catalog) and then the Spring Boot
  BOM, which wins where both manage a version; libraries do not apply the Spring Boot plugin.
- Version comes from git tags via axion-release (`scmVersion`). Never hard-code versions.
- Docker images are separate modules in `images/` that consume an app's `bootJar` through the
  `dockerImageResource` configuration. Registry settings come from Gradle properties
  `com.guslarz.invoicewatcher.docker.{registry,namespace,username,password,tag}`.
- CI (`.github/workflows`): `build-and-analyze` on PRs (job `build` runs `./gradlew build analyze`
  and is skipped when only `docs/` changed; job `reference` checks `docs/reference/` on every PR),
  `snapshot` on push to `master` unless only `docs/` changed, or run by hand (image tag
  `snapshot`), `release` manual (bumps version, publishes images), `docs` builds and
  deploys the documentation site.
