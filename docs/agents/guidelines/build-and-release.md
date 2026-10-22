# Build and release

- Use the wrapper: `./gradlew build analyze`. `analyze` = Checkstyle + SpotBugs (`check`), and
  `build` depends on it.
- Convention plugins live in `buildSrc/src/main/kotlin`; module build files only pick one
  (`spring-boot-app-conventions`, `spring-boot-lib-conventions`, `java-lib-conventions`,
  `docker.image-conventions`) and list dependencies. Shared build logic goes to `buildSrc`, not
  to module build files.
- Dependency versions live in `gradle/libs.versions.toml`; Spring-managed ones have no version.
- Version comes from git tags via axion-release (`scmVersion`). Never hard-code versions.
- Docker images are separate modules in `images/` that consume an app's `bootJar` through the
  `dockerImageResource` configuration. Registry settings come from Gradle properties
  `com.guslarz.invoicewatcher.docker.{registry,namespace,username,password,tag}`.
- CI (`.github/workflows`): `build-and-analyze` on PRs (job `build` runs `./gradlew build analyze`
  and is skipped when only `docs/` changed; job `reference` checks `docs/reference/` on every PR),
  `snapshot` on push to `master` unless only `docs/` changed, or run by hand (image tag
  `snapshot`), `release` manual (bumps version, publishes images), `docs` builds and
  deploys the documentation site.
