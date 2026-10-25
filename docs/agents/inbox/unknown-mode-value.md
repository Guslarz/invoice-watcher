---
title: Unknown mode value fails with an unrelated error
kind: bug
area: build
added: 2026-10-25
---

`invoice-watcher.event-producer.mode` and `invoice-watcher.mail-producer.dao.mode` select a backend
through `@ConditionalOnProperty(havingValue = ...)`. An unknown or missing value (e.g.
`dao.mode=jdbc`) activates no backend, so the application fails later with an unrelated
missing-bean error instead of naming the property. The `mode` records in the `api` modules are
never bound, so property validation does not cover them. Spec clauses EVENT-010 and USER-010 do
not say what happens with an unknown value.
