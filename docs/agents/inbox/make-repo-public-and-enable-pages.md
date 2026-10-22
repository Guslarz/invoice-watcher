---
title: Make the repository public and enable GitHub Pages
kind: ops
area: docs
added: 2026-10-13
---

The documentation site is built by `.github/workflows/docs.yml`, but it is not deployed yet: the
repository is private and GitHub Pages is disabled. Do it once the site has meaningful content
(specs, guides, generated reference).

Before going public, make sure the AES key was rotated (the old one is in git history, see
`docs/setup/keys-and-users.md`) and check the history for other secrets.

Steps:
1. Settings → General → Change visibility → Public.
2. Settings → Pages → Source: GitHub Actions
   (or `gh api -X POST repos/Guslarz/invoice-watcher/pages -f build_type=workflow`).
3. Set the repository variable `PAGES_ENABLED=true` (`gh variable set PAGES_ENABLED --body true`)
   — the `deploy` job in `docs.yml` is skipped until then.
4. Re-run the Docs workflow on `master` and check https://guslarz.github.io/invoice-watcher/.
