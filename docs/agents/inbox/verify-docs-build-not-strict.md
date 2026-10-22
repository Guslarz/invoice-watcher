---
title: verify.sh does not catch documentation site build errors
kind: bug
area: build
added: 2026-10-22
---

`scripts/verify.sh` can pass while the `Docs` workflow fails (seen on PR #12: a relative link in
an included spec snippet broke `docs/system/index.md`). Two causes:

- `mkdocs build --strict --quiet`: in MkDocs 1.6 `--quiet` drops warnings before `--strict`
  counts them, so the strict build never fails. Drop `--quiet` (or filter output instead).
- When `mkdocs` is not installed the step prints `SKIP` and the script still exits 0, so the
  overall result looks like a pass. Fail instead (with an explicit opt-out), or install the
  pinned `requirements-docs.txt` into a local virtualenv / run it in a `python` container.
