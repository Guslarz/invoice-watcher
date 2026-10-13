---
title: Make the published docs task-oriented and human readable
kind: idea
area: docs
added: 2026-10-13
---

The GitHub Pages site should read as a manual for people, not as agent material: focus on usage
and the concrete steps someone has to take, e.g. configure the app (mailbox, folders, encrypting
the mailbox password with `secret-helper-app`, properties), run it locally, and deploy it
(GitHub secrets and variables, the `Environment` workflow, what gets created, how to tear it down).

Points to settle when planning the guides (`docs/guide/`, `docs/operate/`, `docs/develop/`):
- Structure pages as how-tos (goal → prerequisites → numbered steps → how to verify → troubleshooting)
  rather than descriptions of modules.
- Specs (`docs/specs/`) are normative and terse. Decide whether they stay a top-level section of
  the site or move to a secondary "Specifications" area, with guides as the entry point. This
  touches ADR-0001 ("publish specs as-is").
- Snippets from specs must still read naturally inside a how-to; prefer linking over embedding
  when they do not.
