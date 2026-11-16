---
title: Verify IntelliJ resolves Secret configuration properties after Gradle reload
kind: tech-debt
area: build
added: 2026-11-16
status: ready
size: S
blocked-by: []
---

Confirm that IntelliJ resolves `invoice-watcher.google-oauth.client-secret` in the registration
backend's linked `application-local.yml` after Reload All Gradle Projects. The shared Spring
build convention now describes `Secret` properties as strings in generated and packaged metadata;
runtime types remain `Secret`. Build verification passed, but the IDE warning disappearing has
not been confirmed. Check Gradle build delegation and the YAML file's Spring/module association,
including whether the link into sibling master affects discovery. Compare with `client-id` in
the same file and record or fix any remaining IDE integration issue without exposing credentials.
