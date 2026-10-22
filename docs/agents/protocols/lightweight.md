# Protocol lightweight

For changes that hit no planning trigger: fixes restoring spec'd behavior, refactors, dependency
bumps, build tweaks, doc typos. If during work you discover a spec clause must change, stop and
switch to Protocol plan.

## Steps

1. Branch from an up-to-date `master`: `scripts/worktree.sh new <fix|chore|docs>/<slug>` and work in
   the printed worktree (plain clone without worktrees: `git switch -c <fix|chore|docs>/<slug>`).
2. Make the change. A bug fix gets a regression test; name the violated clause if there is one.
3. If the change addresses an inbox note, delete the note.
4. Run `scripts/verify.sh`.
5. Stop and ask the developer to review the working tree. Only after approval: commit, push,
   open a pull request to `master`. Never merge it.
