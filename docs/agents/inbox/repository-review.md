---
title: Review naming, code logic, build and directory structure
kind: tech-debt
area: build
added: 2026-10-13
---

Review the whole repository and list what is worth improving. Check:
- **Naming**: whether module, package, class, property, directory and resource names are correct,
  precise and consistent with each other and with the specs.
- **Code logic**: unnecessary complexity, dead or unfinished code, error handling.
- **Build**: convention plugins in `buildSrc/`, dependency declarations, Gradle deprecations.
- **Directory structure**: `apps/`, `libs/`, `images/`, `deployments/`.

Starting points noticed so far (verify, do not assume):
- `deployments/infrastructure/environments/dev/ephemereal` is misspelled (ephemeral); the name is
  also used in `.github/workflows/environment.yml` and `docs/agents/guidelines/deployments.md`.
- Unfinished code: `// TODO more than basic` in `MailProducerDaoJpaAutoConfiguration`
  (`docs/agents/inbox/jpa-cloud-sql-data-source.md`), `generate-key --mode random`
  (`docs/agents/inbox/secret-helper-generate-key-modes.md`).
- `secret-helper` without a command exits with status 1 and prints nothing.
- Renaming `deployments/k8s-apps` subdirectories is already a separate note
  (`docs/agents/inbox/k8s-apps-directory-names.md`).

Output: one inbox note per concrete finding (or an extension of an existing note), not a single
large change.
