---
id: ADR-0008
status: accepted
date: 2026-10-24
---

# ADR-0008: Build the documentation site with Zensical

## Context

The site was built with MkDocs 1.6 and Material for MkDocs. MkDocs 2.0 removes the plugin system
and theme overrides with no migration path, and the Material team recommends its successor,
Zensical. Options considered:

- Pin MkDocs 1.x: works today, but it is a dead end without security or compatibility fixes.
- GitBook: hosted, with its own markup; its one-way Git sync conflicts with the generated
  `docs/reference/` pages and with changing documentation only through pull requests.
- Zensical: reads the same Markdown, Python Markdown extensions and navigation, from the
  Material team; configured by `zensical.toml`.

## Decision

- Build the site with Zensical, configured by `zensical.toml`, version pinned in
  `requirements-docs.txt`.
- Build strictly (`zensical build --strict`) in `scripts/verify.sh` and in the `Docs` workflow,
  so a broken link or anchor fails both. `scripts/verify.sh` installs Zensical into the
  git-ignored `.venv-docs/` of the working tree and never skips the build.

## Consequences

- Markdown sources and the published layout stay unchanged.
- Zensical is pre-1.0: upgrades are deliberate version bumps, checked against the rendered site.
- Zensical validates fewer things than MkDocs did (pages missing from the navigation, links to
  directories or `.html` files); the gaps are tracked as inbox notes.
- Local verification needs `python3` with `venv` (or `virtualenv`).
