---
title: Move hardcoded versions into the version catalog
kind: tech-debt
area: build
added: 2026-10-24
---

The build guideline says versions live in `gradle/libs.versions.toml`, but:
- `jvm/code-analysis-conventions.gradle.kts:11`: Checkstyle `toolVersion = "11.0.0"`.
- Unused catalog entry: `spring-cloud-gcp-starter-firestore` (only `-datastore` is used).
- The catalog has no `[versions]` section, so related artifacts cannot share one version.
