---
title: Complete WCAG 2.2 AAA accessibility and agent design guidance
kind: tech-debt
area: build
added: 2026-11-15
status: later
size: M
blocked-by: []
---

The developer requires WCAG 2.2 AAA across InvoiceWatcher-controlled frontend pages and complete
workflows on desktop and mobile. Current SPEC-REG REG-009 and the frontend guideline target AA;
AAA conformance has not been established. Update the spec, ADR-0013 accessibility policy,
frontend agent guidance and corresponding user documentation together when implementing this work.
Google-hosted account/consent pages are external to the application's conformance scope.

Audit every applicable WCAG 2.2 A/AA/AAA criterion for registration, folder editing, connection,
disconnection, deletion and sign-out, including loading, empty, success, confirmation and error
states. Record criterion applicability, automated/manual assessment, actual evidence and remaining
gaps; missing evidence is not a pass. Correct findings rather than treating passing axe checks as
proof of conformance. Include accessible authentication and error prevention, labels/instructions,
landmarks/language/headings, readable help, associated validation errors, restrained live-region
announcements and predictable keyboard focus/restoration.

Give agents concrete component-design instructions: normal text contrast at least 7:1, large text
at least 4.5:1, applicable AAA 44-by-44 CSS-pixel target sizing or justified exceptions, keyboard
and touch parity, no color-only signals, reduced motion and accessible timing. Verify 200% text
resizing, text-spacing overrides, 400% browser zoom/reflow at 320 CSS pixels, both orientations,
focus contrast/area/obscuration, and screen-reader use. Preserve the requested 1px focus outline
with a 2px gap, but assess AAA indicator area explicitly and add another indicator if necessary;
that thin outline alone must not be assumed sufficient.

Use the existing native catalogue, Playwright workflow fixtures and visual baselines. Enable all
relevant supported WCAG A/AA/AAA axe rules, including enhanced contrast, fail verification for
automatically detected violations, and add focused regression tests for audit findings. Maintain
a dated manual assessment matrix with browser/device/assistive-technology combinations and
reproducible steps; do not invent results for unavailable technologies or claim full AAA before
assessment is complete. Keep component TS/HTML/SCSS separation, signal stores and typed API access.
Storybook/axe integration remains separate in storybook-accessibility-testing.md; this work does
not depend on that migration. The native datepicker replacement is separately deferred.

[WCAG 2.2](https://www.w3.org/TR/WCAG22/) and
[Understanding conformance](https://www.w3.org/WAI/WCAG22/Understanding/conformance).
