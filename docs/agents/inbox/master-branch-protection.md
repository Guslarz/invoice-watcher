---
title: Protect master on GitHub
kind: ops
area: build
added: 2026-10-22
---

`master` has no branch protection or ruleset; the GitHub API answers that the feature needs GitHub
Pro or a public repository. The "never push to master" rule for agents is enforced only by
`AGENTS.md` and the `ask` permission rules in `.claude/settings.json`, which match command text and
can be bypassed (`git -C . push`). Once the repository is public
(`make-repo-public-and-enable-pages.md`), require pull requests and passing CI for `master`.
