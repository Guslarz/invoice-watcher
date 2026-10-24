---
title: Clean up the Docker image convention and Dockerfile
kind: tech-debt
area: build
added: 2026-10-24
---

`buildSrc/src/main/kotlin/docker/`:
- `image-conventions.gradle.kts:30` reads `imageVersion.get()` and `:58` puts
  `ZonedDateTime.now()` into a filter at configuration time: the output is not reproducible and
  `copySources` up-to-date checking is unclear.
- `:86-94` registers its own `build` and `publish` tasks instead of applying `base`, so image
  modules have no `clean` or `assemble`.
- `:81-84` `dockerPushImage` depends on `dockerSaveImage`, which push does not need; `build`
  saves a `.tar.gz`, so `./gradlew build` needs a Docker daemon.
- `util.kt:7` `DockerImageResourceType.Statics` is unused.
- The image name comes from the project name, so the registry image is
  `ghcr.io/guslarz/invoice-watcher/mail-producer-docker` — the build module suffix leaks into the
  published name. Renaming the image changes the deployment (Protocol plan).

`images/mail-producer-docker/src/Dockerfile`: `CMD java -jar app.jar` is in shell form, so java is
not PID 1 and does not receive SIGTERM (graceful shutdown in k8s); use the exec form. The jar name
`mail-producer-app-@PROJECT_VERSION@.jar` is coupled to the app's project name.
