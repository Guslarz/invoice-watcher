---
title: IMAP session properties are probably ignored
kind: bug
area: mail-scanning
added: 2026-10-24
---

`MailConnectionFactoryImpl` (lines ~45-82), to verify against Jakarta Mail:
- It sets `mail.imap.*` keys, but the protocol is `imaps` (line 24); Jakarta Mail reads
  `mail.imaps.*` for it, so the keys likely have no effect.
- `props.put("mail.imap.port", properties.port())` stores an `int`; `Properties.getProperty`
  returns `null` for non-String values.
- `starttls.enable` makes no sense with implicit TLS (`imaps`).
- `PasswordAuthenticator` is redundant: `store.connect(host, port, user, password)` passes the
  credentials explicitly. Because of it, two visitors (`CreateSessionVisitor`, `ConnectVisitor`)
  exist for one credentials type.

A GreenMail test (see `spec-test-coverage.md`) would pin down which settings matter.
