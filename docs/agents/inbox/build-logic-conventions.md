---
title: Move the buildSrc conventions to gradle/build-logic
kind: tech-debt
area: build
added: 2026-10-24
---

Module discovery already lives in the included build `gradle/build-logic/settings`. The convention
plugins in `buildSrc` could move to `gradle/build-logic` as well (e.g. `gradle/build-logic/conventions`,
included through `pluginManagement`), so all build logic sits in one place, is built the same way and
can be tested with TestKit. Update `docs/agents/guidelines/build-and-release.md` and the repository
map in `AGENTS.md` accordingly.
