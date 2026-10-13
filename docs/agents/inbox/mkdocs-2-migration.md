---
title: Decide the documentation tool before MkDocs 2.0
kind: tech-debt
area: docs
added: 2026-10-13
---

Material for MkDocs warns that MkDocs 2.0 removes the plugin system and theme overrides with no
migration path (https://squidfunk.github.io/mkdocs-material/blog/2026/02/18/mkdocs-2.0/). The site
pins `mkdocs==1.6.1` in `requirements-docs.txt`. Evaluate the successor recommended by the
Material team (Zensical) before relying on more plugins (e.g. generated reference pages).
