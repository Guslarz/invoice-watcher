---
title: Enforce dependency grouping in Gradle module build files
kind: tech-debt
area: build
added: 2026-11-12
status: later
size: M
blocked-by: []
---

Module build files conventionally separate project API dependencies, project implementation
dependencies, Spring libraries, other external libraries and test-scope dependencies with blank
lines. This layout is currently manual and can drift when adding modules or dependencies.

Document the canonical grouping and order, then choose a repository-aware formatter or check that
can distinguish module roles and resolved library coordinates rather than relying only on alias
suffixes. Include existing implementations such as secret/aes whose names do not end in impl.
Decide ordering within groups and treatment of runtimeOnly, compileOnly, platforms/BOMs, bundles,
comments and build-logic-specific declarations. Preserve dependency scopes and BOM precedence.

Integrate enforcement into scripts/verify.sh and CI with actionable file/group diagnostics. Evaluate
an explicit fix command for safe layout-only changes. Cover representative positive and negative
fixtures so the check accepts supported Kotlin DSL constructs and does not change dependency meaning.
