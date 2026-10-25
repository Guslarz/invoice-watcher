---
title: IMAP connection has no timeouts
kind: bug
area: mail-scanning
added: 2026-10-25
---

`MailConnectionFactoryImpl` sets no `mail.imaps.connectiontimeout` or `mail.imaps.timeout`. In
`com.sun.mail:jakarta.mail:2.0.2`, both default to infinite (`SocketFetcher`), so a server that
accepts the connection and then stops answering blocks the scan indefinitely. To settle: pick the
values, decide whether they are configurable (`invoice-watcher.` properties), and specify them in
`docs/agents/specs/mail-scanning.md`.
