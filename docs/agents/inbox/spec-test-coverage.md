---
title: Cover existing spec clauses with tests
kind: tech-debt
area: build
added: 2026-10-13
---

The retrofitted specs (`docs/specs/`) describe behavior that is almost untested: the only tests
are `AesUtilTest` (`libs/util/aes`) and `BinarySearchEngineTest` (`libs/mail-connector/impl`),
and neither names a clause. Protocol implement requires a test naming every *changed* clause, but
the clauses written in the retrofit have no tests at all.

To settle:
- Add tests for the acceptance criteria of each spec, named after the clause
  (`@DisplayName("MAIL-020 ...")`), e.g. mail scanning against a GreenMail IMAP server, event
  publishing against an embedded or Testcontainers Kafka, user configuration against the
  Datastore emulator and H2, `secret-helper` commands.
- Gap clauses (`*(gap: ...)*`) get their tests together with the fix.
- Clauses that cannot be tested in code (DEPLOY-*, RELEASE-*) are verified by the workflows or
  marked as manually checked.
- Optionally extend `scripts/check-docs.sh` to report clauses without a test that names them.
