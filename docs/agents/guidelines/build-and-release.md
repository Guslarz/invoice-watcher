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
- Settings plugins live in the included build `gradle/build-logic/settings` (wired through
  `pluginManagement { includeBuild(...) }`, because `buildSrc` is built after settings) and are
  tested with Gradle TestKit; the root `check` runs their tests.
- Java 25 via toolchain; repositories only in `dependencyResolutionManagement` of
  `settings.gradle.kts` (project repositories fail the build).
- Dependency versions live in `gradle/libs.versions.toml`; Spring-managed ones have no version.
  Spring modules import the `spring-cloud-gcp-dependencies` BOM (catalog) and then the Spring Boot
  BOM, which wins where both manage a version; libraries do not apply the Spring Boot plugin.
- Version comes from git tags via axion-release (`scmVersion`). Never hard-code versions.
- Docker images are separate modules in `images/` that consume an app's `bootJar` through the
  `dockerImageResource` configuration. An image module is named `<image>-docker`; the published
  image is `<image>`, without the suffix. Registry settings come from Gradle properties
  `com.guslarz.invoicewatcher.docker.{registry,namespace,username,password,tag}`. The jar is
  copied next to the Dockerfile as `app.jar`; the version and build time come in as build args
  `PROJECT_VERSION` and `BUILD_DATE`. `assemble` builds the image (needs a Docker daemon),
  `publish` pushes it when a registry is set.
- CI (`.github/workflows`): `build-and-analyze` on PRs (job `build` runs `./gradlew build analyze`
  and is skipped when only `docs/` changed; job `reference` checks `docs/reference/` on every PR),
  `snapshot` on push to `master` unless only `docs/` changed, or run by hand (image tag
  `snapshot`), `release` manual (bumps version, publishes images), `docs` builds and
  deploys the documentation site.
