# Build and release

- When adding third-party tooling or dependencies, review SBOM coverage under
  [ADR-0029](../adr/0029-review-sbom-coverage-when-adding-third-party-tools-and-dependencies.md).

- Use the wrapper: `./gradlew build analyze`. `analyze` = Checkstyle + SpotBugs only, without
  tests; `build` runs them too (through `check`). Any Checkstyle violation fails the build
  (`maxWarnings = 0`).
- Convention plugins live in `gradle/build-logic/conventions/src/main/kotlin`, grouped by package, so the ID is
  `<package>.<name>-conventions` (`jvm`, `springboot`, `docker`; not `java`, which the JVM reserves).
  Module build files only pick one (`springboot.app-conventions`, `springboot.lib-conventions`,
  `jvm.lib-conventions`, `docker.image-conventions`) and list dependencies. Shared build logic
  goes to the conventions included build, not to module build files. Precompiled scripts read the catalog with
  `versionCatalogs.named("libs")`.
- Settings plugins live in the included build `gradle/build-logic/settings` and are tested with
  Gradle TestKit; the root `check` runs their tests. Both build-logic builds are wired through
  `pluginManagement { includeBuild(...) }`.
- The configuration cache is on (`gradle.properties`); a problem it reports fails the build. Build
  logic must not read or configure other projects (no `allprojects`/`subprojects`, `rootProject`
  only through `isolated.rootProject`), so the build can move to Isolated Projects. The one
  exception, the version in the root `build.gradle.kts`, and the plugins that still block
  Isolated Projects are listed in `docs/agents/backlog/isolated-projects.md`.
- `docs.reference-conventions` (applied by `jvm.common-conventions` and `docker.image-conventions`)
  gives every module a `docsReference` task, which writes the module's facts to
  `build/docs-reference/` for `scripts/gen-reference.py`. `./gradlew docsReference` runs it in all modules.
- Java 25 via toolchain; repositories only in `dependencyResolutionManagement` of
  `settings.gradle.kts` (project repositories fail the build).
- Dependency versions live in `gradle/libs.versions.toml`; Spring-managed ones have no version.
  Spring modules import the `spring-cloud-gcp-dependencies` BOM (catalog) and then the Spring Boot
  BOM, which wins where both manage a version; libraries do not apply the Spring Boot plugin.
  Included build-logic test suites also import the catalog's Spring Boot BOM instead of pinning
  separately managed test libraries such as AssertJ.
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
  `snapshot`), `release` manual on master (creates release/restoration commits and a tag, publishes images), `docs` builds and
  deploys the documentation site.

- Frontend conventions provision pinned Node/pnpm locally through Gradle and use a frozen pnpm
  lockfile. Browser checks and snapshot updates run their servers and Playwright inside a Docker
  image version-matched to @playwright/test, with the exact workspace Node/pnpm pins. Do not install
  browser binaries or OS libraries on the host, or duplicate frontend setup in CI actions. Run
  containers as the host user, retain failure artifacts, mount baselines read-only for checks and
  writable only for explicit snapshot updates. An accessible Docker daemon is required. Backend
  tests manage Datastore through Testcontainers. Node archives are declared only in settings,
  respecting FAIL_ON_PROJECT_REPOS.

- `frontend.workspace-conventions` is applied once by `frontend/build.gradle.kts` (:frontend).
  It owns Node/pnpm setup, the frozen workspace installation, shared UI/catalogue builds and
  the shared browser test image. App wrappers apply `frontend.app-conventions`, declare their Angular
  target/output and browser configuration, and consume managed tool artifacts from :frontend.
  Never install the same workspace separately from each application. Use distinct application
  output directories and non-conflicting browser-test servers for parallel checks.

Release uses `scripts/release.py` to ask axion-release for the next version, commit the explicit
deployment selection, invoke its local `createRelease` task, and restore development in a
later commit. It atomically pushes both commits and the tag using the dedicated release App,
then checks out the tag to build and publish. Never force a tag or branch update. A retry uses
the original workflow source SHA recorded in the release commit; a newly dispatched stale run fails.
The App setup and ruleset exception are documented in the one-time setup instructions.

- Mode metadata is generated from implementation conditions during compilation. The application
  convention merges classpath contributions into its own metadata; the compiler's runtime-metadata
  classpath is a declared cache input. Shared APIs remain free of runtime selector registration.
  Metadata conflicts fail generation instead of depending on classpath order.
