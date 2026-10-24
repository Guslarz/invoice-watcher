---
title: Report documentation pages missing from the navigation
kind: tech-debt
area: docs
added: 2026-10-24
---

MkDocs reported published pages that are not in `nav` (`validation.omitted_files: warn`), so the
strict build failed on them. Zensical 0.0.72 has no such check: a page under `docs/` that is not
listed in `zensical.toml` is built silently and is only reachable by search. Add the check to
`scripts/check-docs.sh` (every published `docs/**/*.md` outside `agents/` appears in the nav), or
enable it in Zensical once it supports it.
