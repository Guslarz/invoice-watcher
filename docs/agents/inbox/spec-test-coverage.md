---
title: Cover existing spec clauses with tests
kind: tech-debt
area: build
added: 2026-10-13
---

The retrofitted specs (`docs/specs/`) describe behavior that is almost untested: there are five
test classes (`AesUtilTest`, `BinarySearchEngineTest`, `MailConnectionImplTest`,
`KafkaEventProducerTest`, `MailProducerTaskImplTest`), only some name a clause, 12 of 16 modules
have no tests, and no test starts a Spring context (mode switching and property binding are
untested). Protocol implement requires a test naming every *changed* clause, but
the clauses written in the retrofit have no tests at all.

To settle:
- Add tests for the acceptance criteria of each spec, named after the clause
  (`@DisplayName("MAIL-020 ...")`), e.g. mail scanning against a GreenMail IMAP server, event
  publishing against an embedded or Testcontainers Kafka, user configuration against the
  Datastore emulator and H2, `secret-helper` commands.
- Gap clauses (`*(gap: ...)*`) get their tests together with the fix.
- Every clause is either covered by a test that names it or carries an explicit, stated reason
  why not: e.g. a marker next to the clause like `*(untested: <reason>)*`. This applies
  to clauses that cannot be tested in code (DEPLOY-*, RELEASE-*: verified by the workflows or
  checked manually) and to any clause deliberately left without a test. A missing test
  must never be silent.
- Extend `scripts/check-docs.sh` (run by `scripts/verify.sh`) to fail on clauses that have
  neither a test naming them nor an `untested` reason. Once that check exists, the rule belongs
  in the spec template and Protocol implement.
