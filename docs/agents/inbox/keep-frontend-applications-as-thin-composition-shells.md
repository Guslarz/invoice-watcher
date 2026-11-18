---
title: Keep frontend applications as thin composition shells
kind: tech-debt
area: build
added: 2026-11-18
status: later
size: M
blocked-by: []
---

Keep frontend application sources limited to bootstrap, the root app component, route composition
and application-level configuration. Move feature pages/components, workflow state, services,
models and other substantive behavior into separately owned frontend libraries, even when a
feature currently has only one application consumer. Keep reusable presentation controls in the
shared UI library and domain workflows in feature libraries.

Apply this boundary to the registration frontend without changing its user-visible behavior.
Define library public entry points and dependency direction, update Angular/TypeScript and Gradle
build wiring, and move feature unit tests with their code. Retain application composition and
end-to-end checks at the application boundary; verify that routing, authentication integration
and existing workflows still work after extraction.

Update the [frontend guideline](../guidelines/frontend.md), which currently places mailbox
workflows in the application, and review [ADR-0013](../adr/0013-define-frontend-consistency-through-versioned-design-tokens-and-documentation.md)
and [ADR-0018](../adr/0018-deploy-angular-registration-separately-and-share-ui-tokens-and-components.md)
for alignment. This is a separate structural change from the broader
[Angular/style-guide alignment](enforce-angular-google-typescript-style.md).
