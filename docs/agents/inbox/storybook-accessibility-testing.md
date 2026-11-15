---
title: Replace the native component catalogue with Storybook and axe-core checks
kind: idea
area: build
added: 2026-11-15
status: later
size: M
blocked-by: []
---

Introduce Angular Storybook for the shared UI components, replacing the native Angular catalogue
rather than maintaining two catalogues. Keep business-specific workflows in application tests.
Cover supported variants, loading, empty, error, disabled and focus states, desktop and mobile
viewports, keyboard interaction, style encapsulation and reviewed visual baselines.

Integrate Storybook's accessibility addon backed by axe-core and a compatible CI runner. Configure
available WCAG 2.2 A/AA/AAA checks, including enhanced contrast, and fail CI for detected violations;
a warning-only addon panel is insufficient. Automation complements manual assessment and cannot
establish AAA conformance by itself. Preserve the current catalogue's Playwright/axe checks until
the replacement provides equivalent coverage, including actual rendered Shadow DOM controls.

Select exact compatible stable Storybook/Angular/Node and runner pins during planning. Confirm
Angular runner support rather than assuming the React/Vitest path works. Keep Node, pnpm,
installation and browser setup owned by Gradle's frontend workspace; migrate catalogue serve,
build, test and baseline commands and update ADR-0017 and developer instructions in the same
change. This migration is explicitly deferred from the current registration work.

[Storybook accessibility testing](https://storybook.js.org/docs/writing-tests/accessibility-testing).
