# Protocol lightweight

For changes that hit no planning trigger: fixes restoring spec'd behavior, refactors, dependency
bumps, build tweaks, doc typos. If during work you discover a spec clause must change, stop and
switch to Protocol plan.

## Steps

1. Branch from an up-to-date `master`: `git switch -c <fix|chore|docs>/<slug>`.
2. Make the change. A bug fix gets a regression test; name the violated clause if there is one.
3. If the change addresses an inbox note, delete the note.
4. Run `scripts/verify.sh`.
5. Commit, push, open a pull request to `master`. Never merge it.
