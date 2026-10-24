---
title: Settle and enforce one naming convention for static final fields
kind: tech-debt
area: build
added: 2026-10-24
---

Constants are named two ways. PascalCase holds most of them: bean-name and property-name holders in
the `*AutoConfiguration` and `*Properties` classes (`Prefix`, `Mode`, `DataSource`, …),
`GenerateKeyCommand.PasswordMode`, and `AesUtil.RandomGenerator`. UPPER_SNAKE_CASE, as in Google
Java style, is used in `MailConnectionFactoryImpl.IMAP_PROTOCOL`, `AesUtil.IV_LENGTH`, and test
fixtures (`NOW`, `KEY`, `FOLDER`). Nothing catches this: `config/checkstyle/checkstyle.xml` has
no `ConstantName` module, and `docs/agents/guidelines/code.md` does not mention constant naming.

Pick one convention (Google style says UPPER_SNAKE_CASE) and write it in the coding guideline.
Enable `ConstantName` (and `StaticVariableName` for non-final static fields) in Checkstyle, then
rename the fields that break the rule. Grep for them with
`static final [A-Za-z<>?, ]+ [A-Z][a-z]`.

Related, from the same review: `AesUtil.RandomGenerator` is a public `Random` that
`GenerateKeyCommand` borrows to generate its salt. It could be a private `SecureRandom`, with the
command owning its own instance.
