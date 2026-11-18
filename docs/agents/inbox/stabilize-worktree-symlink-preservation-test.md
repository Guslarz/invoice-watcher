---
title: Stabilize the worktree destination-preservation test against access-time changes
kind: tech-debt
area: build
added: 2026-11-18
status: ready
size: S
blocked-by: []
---

Investigate intermittent failure of
`LocalConfigurationTest.test_WORKTREE_003_preserves_destination_entries` in
`scripts/tests/test_worktree_prune.py`. A full verification run failed for the dangling-symlink
case because only `st_atime` differed between complete `lstat()` results; the next run passed all
script tests without changing worktree code. The test currently treats every metadata field as
immutable, including access time that filesystem inspection may update.

Establish which metadata expresses the destination-preservation contract and make the assertion
reliable while retaining checks for replacement, mutation and symlink-target changes. Verify the
file, directory, symlink and dangling-symlink cases, including execution across timestamp boundaries.
Keep this follow-up separate from the processor changes.
