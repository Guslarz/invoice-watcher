---
title: Tighten the static analysis and Lombok configuration
kind: tech-debt
area: build
added: 2026-10-24
---

- `config/spotbugs/exclude.xml` excludes `EI,EI2` globally and without a comment; the code
  guideline requires a reason for every exclusion. Scope it or document it (records exposing
  lists are the usual cause).
- `config/checkstyle/checkstyle.xml:35-39, 402-406` reference `checkstyle-suppressions.xml` and
  `checkstyle-xpath-suppressions.xml` with relative paths and `optional=true`; the xpath file does
  not exist. Verify whether the relative path resolves (`${config_loc}` would be explicit) —
  otherwise suppressions are silently ignored.
- `libs/mail-producer/dao/firestore-impl/lombok.config` sets `config.stopBubbling = true` without a
  comment, which also drops the root `lombok.copyableAnnotations += ...Qualifier`. No effect today,
  but a trap for the next `@Qualifier` field.
