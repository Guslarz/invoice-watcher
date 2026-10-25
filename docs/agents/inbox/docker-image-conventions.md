---
title: Drop the build module suffix from the published image name
kind: tech-debt
area: build
added: 2026-10-24
---

The image name comes from the project name in `buildSrc/src/main/kotlin/docker/`, so the registry
image is `ghcr.io/guslarz/invoice-watcher/mail-producer-docker` — the build module suffix leaks into
the published name. Renaming the image changes the deployment (Protocol plan): the name also
appears in the dev kustomize overlay, spec RELEASE-002 and the setup and system pages.
