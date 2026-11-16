---
title: Enforce Angular and Google TypeScript style guides across the frontend
kind: tech-debt
area: build
added: 2026-11-15
status: later
size: M
blocked-by: []
---

Complete the frontend alignment with the [Angular style guide](https://angular.dev/style-guide)
and [Google TypeScript guide](https://google.github.io/styleguide/tsguide.html). Prettier already
uses Google's gts formatting settings; this does not enforce semantic rules or establish full
compliance. Do not blindly install gts wholesale: its Node-oriented TypeScript/compiler assumptions
need reconciliation with Angular templates, decorators and the existing workspace.

Audit remaining differences, including modern Angular file/class naming, catalogue source layout,
member ordering/visibility, readonly inputs and injected state, lifecycle interfaces, import and
export conventions, explicit error construction and typed error handling. Keep framework-required
configuration default exports as explicit exceptions. Preserve the developer's explicit return
types, blank line after imports, separate TS/HTML/SCSS, signals, OnPush and UI/store boundaries.
Keep end-to-end tests distinct from colocated unit tests; avoid mechanical renames without updating
all references, exports, Angular target definitions and Gradle inputs.

Select exact compatible TypeScript ESLint and Angular template-lint tooling for the pinned
Angular/TypeScript/Node versions. Document enforceable rules and justified exceptions in the
frontend guideline, integrate checks into the existing Gradle frontend workspace and CI, and
apply migrations in a reviewable change with existing workflow and visual checks. Include a
regression fixture showing that the configured lint rules reject representative violations.
