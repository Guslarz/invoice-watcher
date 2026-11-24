---
title: Select and apply formatting and linting for Gradle Kotlin DSL
kind: tech-debt
area: build
added: 2026-11-18
status: later
size: M
blocked-by: []
---

Research a formatter/linter for the repository's Gradle Kotlin DSL files and apply the selected
approach consistently. Evaluate candidates such as ktlint or ktfmt, including integration through
Spotless where appropriate. Check current official documentation, Gradle/Java/Kotlin compatibility,
Kotlin DSL support, configurable style rules and whether a candidate provides formatting, linting
or both before selecting and pinning versions.

Cover build/settings scripts and Kotlin convention/plugin sources, including the included builds
under `gradle/build-logic`. Exclude generated sources and build outputs. Provide explicit formatting
and non-mutating check tasks, integrate checks into `scripts/verify.sh` and CI through the existing
build conventions, and document the style and local commands. Apply initial formatting as a
reviewable change, preserve build behavior, and verify that representative style violations fail
checks without compromising configuration-cache compatibility.

Include version-catalog TOML ordering in the tooling decision: define and enforce a deterministic
order for aliases within `[versions]`, `[libraries]` and `[plugins]` in `gradle/libs.versions.toml`.
The catalog currently has declarations appended outside its existing order. Evaluate dedicated TOML
support if the Kotlin DSL formatter does not cover catalogs; provide explicit exceptions for any
intentional grouping and apply the agreed ordering consistently.
