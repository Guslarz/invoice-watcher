# Inbox

Ideas, problems and TODOs to address later — one note per file, nothing here is a commitment.
This directory is not published.

- File name: `<slug>.md` (kebab-case, describes the idea, no numbers).
- Content: frontmatter + a few sentences. Use `docs/agents/templates/inbox.md`.
- Adding a note never needs a plan; agents may add one whenever they notice something out of
  scope of the current change (and mention it in their report instead of fixing it).
- To act on a note: start Protocol plan (or lightweight) with it as input; the pull request that
  addresses it deletes the note. A note that will not be done is deleted with a one-line reason
  in the commit message.

## Triage

Every note says when it can be done, so the next topic (or a batch for Protocol parallel) is picked
without rereading all notes. `python3 scripts/inbox.py [ready|waiting|later]` prints the overview;
it is generated on demand, not committed. `scripts/check-docs.sh` validates the fields.

- `status`:
  - `ready` — nothing blocks it and the note says what to do and what has to be decided, so an
    agent can start Protocol plan or lightweight without first asking what the note means.
  - `waiting` — blocked by something outside the repository, named in `waiting-for` (a release,
    an environment running with real users). Recheck it when that happens, not on a schedule.
  - `later` — a roadmap idea, too vague or low value for now, or blocked by other notes. New
    notes start here unless they are clearly `ready`.
- `size`: `S` (one small pull request), `M` (one pull request, needs some decisions), `L` (several
  pull requests: split it in its plan).
- `blocked-by`: slugs of notes that must be done first. A `ready` note is never blocked. The pull
  request that deletes a note removes its slug from the `blocked-by` of other notes and
  re-triages them.
- While triaging, merge notes that describe the same change, and delete notes that will not be done.
