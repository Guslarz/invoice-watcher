# Protocol parallel

Use when the developer hands over several independent topics at once (e.g. a batch of inbox
notes) and wants to keep discussing requirements while approved work proceeds in the background.
It wraps the other protocols; it does not replace them.

## Roles

- **Dispatcher** — the developer's interactive session in the `master` worktree. Triages topics,
  discusses requirements, drafts plans, starts workers. Never implements.
- **Worker** — a background Claude Code session (`claude --bg`) in its own worktree, one per topic.
  Runs Protocol implement or Protocol lightweight and ends at its review gate.

One topic = one branch = one worktree (`../<slug>` next to `master/`) = one worker.
`scripts/worktree.sh` creates, lists and removes worktrees; it is also the `WorktreeCreate` /
`WorktreeRemove` hook, so worktrees Claude Code creates itself (`claude -w`, background sessions,
subagents) land in the same layout. Worktree name `<type>-<slug>` gives branch `<type>/<slug>`.

## Dispatcher steps

1. **Triage.** Run `scripts/worktree.sh prune` first and report the worktrees it kept (it does
   nothing below 10 worktrees besides `master`). For each topic decide the protocol (`AGENTS.md` → Workflow selection). Topics that
   touch the same files (same spec, module or k8s base) are merged into one topic or queued one
   after another — never run in parallel.
2. **Lightweight topics** need no discussion. The worker creates its own worktree through the hook:
   `claude -w <type>-<slug> --bg --name <slug> "/lightweight <topic>"`. Then continue with step 6.
3. **Plan topics** are discussed one at a time. Draft the plan from `docs/agents/templates/plan.md`
   in the conversation; the developer's approval of the plan content replaces step 5 of Protocol
   plan.
4. Create the worktree with `scripts/worktree.sh new <type>/<slug>` and write the approved plan to
   `docs/agents/projects/<slug>.md` inside it. Run `scripts/check-docs.sh` there.
5. Start the worker from the new worktree:
   `cd ../<slug> && claude --bg --name <slug> "/implement docs/agents/projects/<slug>.md"`.
   A background session started in a worktree other than `master` edits it in place and does not
   create another one. Report the worker name to the developer.
6. Keep at most 4 workers running `scripts/verify.sh` at the same time (one Gradle daemon each).
7. On request, show the state: `scripts/worktree.sh list` (branch, dirty, plan, awaiting-commit,
   pushed, PR) and `claude agents` (session state).

## Worker rules

- Work only inside your worktree. The commit gate from `AGENTS.md` is unchanged: stop with the
  changes uncommitted, write `.gitmessage`, summarize and wait. The developer reviews by attaching
  (`claude attach <slug>`) and approves there.
- Before pushing: `git fetch origin && git rebase origin/master`, then `scripts/verify.sh` again.
  Conflicts in `docs/reference/` are resolved by regenerating it, never by hand.
- Out-of-scope findings go to `docs/agents/inbox/` in your branch, as usual.

## Cleanup

After the pull request is merged, the developer or the dispatcher runs
`scripts/worktree.sh rm <slug>`, then `claude rm <slug>` for the session. The script refuses when
the worktree has uncommitted changes or commits that are neither merged nor pushed. `claude rm`
alone keeps a hook-created worktree that still has files; deleting the session in `claude agents`
runs the same check through the `WorktreeRemove` hook. Worktrees of finished subagents are not
removed automatically. `scripts/worktree.sh prune` removes in one go, without asking, every
worktree `rm` would remove whose background session is `done` (the current one excepted), deletes
those sessions with `claude rm` and lists the kept worktrees with the reason. It runs only from 10
worktrees besides `master` on; `prune --all` ignores that threshold.
