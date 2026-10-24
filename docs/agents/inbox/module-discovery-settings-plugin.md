---
title: Move module discovery out of settings.gradle.kts into a settings plugin
kind: tech-debt
area: build
added: 2026-10-24
---

The module discovery functions (`registerModules`, `internalRegisterModules`, `registerModule`)
live inline in `settings.gradle.kts` and have no tests. `buildSrc` cannot host them, because it is
built after settings are evaluated. Move them to a precompiled `*.settings.gradle.kts` plugin in an
included build (e.g. `build-logic/settings`, wired via `pluginManagement { includeBuild(...) }`)
and cover the plain-file and colliding-path cases with Gradle TestKit. Consider moving the
`buildSrc` conventions to `build-logic` at the same time.
