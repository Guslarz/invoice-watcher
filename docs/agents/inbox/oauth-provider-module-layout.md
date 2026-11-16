---
title: Group OAuth modules by provider under libs/oauth
kind: tech-debt
area: build
added: 2026-11-16
status: ready
size: M
blocked-by: []
---

Google is the first OAuth provider, but more providers are expected. Reflect this in the module
layout: move `libs/google-oauth/{api,impl}` to `libs/oauth/google/{api,impl}` and establish the
convention for future provider modules under `libs/oauth/<provider>`. Update discovered Gradle
project paths, type-safe dependency accessors, relevant package names, metadata type references,
tests, documentation and generated reference pages together. Preserve existing Google behavior
and configuration properties; adding other providers or introducing shared OAuth abstractions
requires separate scope. Coordinate with the broader naming work in
[naming-conventions.md](naming-conventions.md).
