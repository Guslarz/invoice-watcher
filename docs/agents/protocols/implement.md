# Protocol implement

Input: an approved plan `docs/agents/projects/<slug>.md`.
Output: a pull request to `master` containing spec, code, tests and docs — and no plan file.

## Steps

1. Make sure you are on the plan's branch (never on `master`). If missing, create it from an
   up-to-date `master`.
2. **Spec first.** Apply the plan's spec changes to `docs/agents/specs/`. Set
   `status: implemented` only at the end, when tests pass.
3. **Code and tests.** Every changed clause gets at least one test that names it, e.g.
   `@DisplayName("MAIL-004: skips folders that do not exist")`.
4. **Docs.** For impact `BEHAVIOR` / `OPERATIONS` update the listed `system/` / `setup/` /
   `results/` pages that describe the changed behavior — in prose for users, without quoting or
   linking specs and ADRs. Add the ADR if planned.
5. **Verify.** Run `scripts/verify.sh`. Fix failures; do not weaken checks.
6. **Delete the plan file** `docs/agents/projects/<slug>.md` and the inbox note it came from,
   if any. Git history keeps both.
7. Stop and ask the developer to review the working tree (summarize `git status` / diff stat).
   Only after approval: commit, push the branch and open a pull request to `master`
   (`gh pr create --base master`). Never merge it.
8. Report with this block:

```
STATUS: <done | blocked>
BRANCH / PR: <branch> / <url>
SPEC TRACEABILITY:
  <CLAUSE-ID> -> <TestClass#method>
DOCS UPDATED: <paths>
VERIFY: <pass | fail + summary>
DEVIATIONS FROM PLAN: <none | list>
```
