---
title: Check documentation links that do not point to Markdown files
kind: tech-debt
area: docs
added: 2026-10-24
---

MkDocs warned about relative links it could not resolve to a page, such as `system/` or
`page.html` (`validation.unrecognized_links: warn`). Zensical 0.0.72 validates links to `.md`
files and their anchors only; `[x](system/nothing/)` or `[x](nothing.html)` build without an
issue under `--strict`. Absolute links are rejected, which is stricter than before. Add a check to
`scripts/check-docs.sh` that relative links in published pages end in `.md` (with an optional
anchor), or enable it in Zensical once it supports it.
