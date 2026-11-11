---
title: Cover existing spec clauses with tests
kind: tech-debt
area: build
added: 2026-10-13
status: ready
size: L
blocked-by: []
---

The retrofitted specs (`docs/agents/specs/`) describe behavior that was long under-tested. Clause-named
coverage has grown since this note was added, but it is still incomplete and uneven across specs.

Current stage (this change): add clause-named tests for existing `SPEC-USER` and `SPEC-SECRET`
behavior where applicable, including property binding, backend selection and `secret-helper` CLI
behavior. Do not add mail/event tests or repository-wide coverage enforcement here.

Remaining work:
- Add clause-named tests for the remaining specs and clauses not covered by this stage, including
  mail scanning and event publishing acceptance criteria (e.g. GreenMail IMAP, embedded or
  Testcontainers Kafka) and other user/secret clauses that still lack an executable test.
- Gap clauses (`*(gap: ...)*`) get their tests together with the fix.
- Every clause is either covered by a test that names it or carries an explicit, stated reason
  why not: e.g. a marker next to the clause like `*(untested: <reason>)*`. This applies to
  clauses that cannot be tested in code (DEPLOY-*, RELEASE-*: verified by the workflows or checked
  manually) and to any clause deliberately left without a test. A missing test must never be
  silent.
- Extend `scripts/check-docs.sh` (run by `scripts/verify.sh`) to fail on clauses that have
  neither a test naming them nor an `untested` reason. Once that check exists, the rule belongs
  in the spec template and Protocol implement.
