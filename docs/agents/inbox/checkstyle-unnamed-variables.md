---
title: Checkstyle rejects unnamed pattern variables
kind: tech-debt
area: build
added: 2026-10-25
---

The Google Checkstyle config's `PatternVariableNameCheck` (`^[a-z]([a-z0-9][a-zA-Z0-9]*)?$`)
rejects Java's unnamed variable `_`, so type-only `switch` cases need a dummy name such as
`ignored` (`SecretHelperRunner.describe`). Allow `_` in the pattern-variable, lambda-parameter and
local-variable name checks in `config/checkstyle/checkstyle.xml`.
