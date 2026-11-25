---
title: Organize repository-root tooling files by role
kind: tech-debt
area: build
added: 2026-11-18
status: later
size: M
blocked-by: []
---

Review files in the repository root and move supporting tooling configuration into directories
that express its role where practical. Documentation tooling is an initial candidate:
`zensical.toml` and `requirements-docs.txt` could live together under a documentation tooling or
configuration directory. Keep files at the root when discovery conventions or developer workflows
justify it; decide the destination and scope during planning rather than moving every root file.

Update scripts, CI workflows, configuration-relative paths, dependency installation/cache inputs
and contributor instructions together. Preserve the existing verification entry point and the
published site's output, navigation and strict checks. Verify both local documentation builds and
CI entry points after relocation, including execution from the expected working directories.
