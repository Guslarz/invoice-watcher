---
title: Rename the spring convention plugin package to springboot
kind: tech-debt
area: build
added: 2026-10-25
---

Every convention plugin in `buildSrc/src/main/kotlin/spring/` repeats a `boot-` prefix
(`spring.boot-app-conventions`, `spring.boot-lib-conventions`, `spring.boot-common-conventions`).
The package should be `springboot` instead, giving `springboot.app-conventions`,
`springboot.lib-conventions` and `springboot.common-conventions`. Update every module's
`build.gradle.kts`, `AGENTS.md` and `docs/agents/guidelines/` (`build-and-release.md`,
`modules.md`) together.
