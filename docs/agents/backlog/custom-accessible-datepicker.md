---
title: Replace native datepicker with a shared accessible date-time component
kind: idea
area: build
added: 2026-11-15
status: later
size: M
blocked-by: []
---

Replace registration's native datetime-local picker with a shared Angular date-time component
whose calendar, time controls and styling are owned by InvoiceWatcher. Do not delegate the picker
UI to the browser's native popup. Keep the component's TypeScript, HTML and SCSS separate, use
signals and OnPush, and consume the canonical design tokens.

Plan against the frontend WCAG 2.2 AAA target for desktop and mobile: accessible names and format
instructions, keyboard calendar/time navigation and text entry, touch targets, predictable focus
and focus restoration, screen-reader announcements, error association, zoom and reflow. Evaluate
maintained accessible foundations before building calendar interactions from scratch. Choose an
explicit locale, date/time format and timezone contract; preserve local-time-to-instant conversion,
optional blank dates meaning all history, invalid/incomplete input blocking save, and immutable
start dates for saved folders. Do not introduce rescanning semantics as part of this UI change.

Add catalogue fixtures (or Storybook stories if migrated by then), axe-core and keyboard tests,
mobile/desktop visual checks and application integration coverage. Automated results complement
manual assistive-technology assessment; replacing a native picker must not reduce accessibility.
