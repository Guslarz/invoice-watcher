# Protocol lightweight

For changes that hit no planning trigger: fixes restoring spec'd behavior, refactors, dependency
bumps, build tweaks, doc typos. If during work you discover a spec clause must change, stop and
switch to Protocol plan.

## Steps

1. Branch from an up-to-date `master`: `scripts/worktree.sh new <fix|chore|docs>/<slug>` and work in
   the printed worktree (plain clone without worktrees: `git switch -c <fix|chore|docs>/<slug>`).
   A parallel worker uses the worktree already created by its dispatcher instead.
2. Make the change. A bug fix gets a regression test; name the violated clause if there is one.
   A registered worker first sets `implementing` with `python3 scripts/worker.py update <name> --phase implementing`.
3. If the change addresses an inbox note, delete the note.
4. Run `scripts/verify.sh`.
5. Stop and ask the developer to review the working tree. Only after approval: commit, push,
    open a pull request to `master`. Never merge it.
   Write `.gitmessage` and set `awaiting-commit-review` before stopping. After approved commit,
   push and PR creation, set `awaiting-merge --pr <PR-URL>` through the update command. Blockers
   use `blocked --detail <reason>`, not `done`. See Protocol parallel for the lifecycle contract.
