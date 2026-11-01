# Protocol parallel

Use when the developer hands over several independent topics and wants to keep discussing
requirements while approved work proceeds in the background. It wraps the other protocols.

## Roles

- **Dispatcher** — the interactive session in the `master` worktree. Triages topics, discusses
  requirements, drafts plans, creates worktrees and starts workers. Never implements.
- **Worker** — a background Claude Code or OpenCode session in its own worktree. Runs Protocol
  implement or Protocol lightweight and ends at its review gate.

One topic = one branch = one worktree = one worker. Use the backend specified by the developer,
or otherwise use the dispatcher's backend. If the dispatcher's backend cannot be determined,
ask the developer which backend to use; do not guess. Pass the selected `claude` or `opencode`
backend explicitly to the worker script. Optionally select a model independently of the backend; if omitted, the
backend uses its configured default. Claude Code accepts its own model names or aliases;
OpenCode accepts `provider/model` or `provider/model#variant`.
Worker-managed worktrees are exclusively for their registered worker: do not start unmanaged
agent sessions in them. Resume registered workers through the worker script.

## Dispatcher steps

1. **Triage.** For inbox batches use `python3 scripts/inbox.py ready`. Run
   `scripts/worktree.sh prune` and report the worktrees it kept. Decide the protocol for each
   topic. Merge or queue topics that touch the same files; never run them in parallel.
2. **Discuss plan topics** one at a time. Draft the plan from the template in the conversation;
   developer approval of the content replaces the review step of Protocol plan. Lightweight topics
   need no discussion.
3. Create every worktree using `scripts/worktree.sh new <type>/<slug>`. For plan topics write the
   approved plan inside it and run `scripts/check-docs.sh` there.
4. Start the worker from the dispatcher, using the absolute path returned by the worktree script:

   ```sh
   python3 scripts/worker.py start --backend "$BACKEND" --name example --worktree /absolute/path/to/worktree --command '/lightweight description of change'
   ```

   Set `BACKEND` to the selected backend. For a plan use `/implement` followed by its plan path.
   Optionally add `--model <model>` to select a model supported by that backend.
   Report the worker name to the developer. A failed or uncertain launch remains registered: inspect
   it before taking further action rather than starting a second worker in the same worktree.
5. Keep at most four workers running `scripts/verify.sh` simultaneously. This is a dispatcher
   responsibility, not an automatically enforced limit.
6. On request, show `scripts/worktree.sh list` and `python3 scripts/worker.py list`. Inspect one
   worker with `python3 scripts/worker.py status <name>`.

## Worker rules

- Work only inside the assigned worktree. The commit gate in `AGENTS.md` is unchanged: stop with
  changes uncommitted, write `.gitmessage`, summarize and wait. The developer reviews using
  `python3 scripts/worker.py resume <name>` and approves there.
- Before pushing: `git fetch origin && git rebase origin/master`, then run verification again.
  Resolve conflicts in generated reference pages by regenerating them, never by hand.
- Out-of-scope findings go to the inbox in your branch as usual.

## Cleanup

The worker registry is stored in the common Git directory, outside tracked files. Status `unknown`
means the backend could not establish the state, including when OpenCode's server changed. It is
not permission to delete a worker or worktree. Investigate using the native agent tools.

After the PR is merged, confirm the session is finished and run
`python3 scripts/worker.py remove <name>`, then `scripts/worktree.sh rm <slug>`. Removing a worker
deletes its agent session but retains its worktree. Worktree removal refuses registered workers and
still checks for uncommitted or unpushed changes. Automatic pruning removes only registered
worktrees whose worker is confirmed finished, whose session is not attached through the script,
and whose Git state is safe. It keeps unregistered worktrees and workers with unknown state. It
assumes the exclusive-use rule above; unmanaged sessions cannot be detected reliably. It runs at
ten or more non-master worktrees, or at any count with `prune --all`. If the remote cannot be
fetched, it removes nothing. Review unregistered sessions before explicitly removing worktrees.
