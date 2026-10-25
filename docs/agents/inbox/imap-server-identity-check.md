---
title: IMAP server hostname is not verified
kind: bug
area: mail-scanning
added: 2026-10-25
---

`MailConnectionFactoryImpl` connects over `imaps` with an empty `Session`. In
`com.sun.mail:jakarta.mail:2.0.2`, `mail.imaps.ssl.checkserveridentity` defaults to `false`
(`SocketFetcher`), so the TLS certificate is checked against the trust store, but not against the
host it was issued for. Any trusted certificate is accepted, and that leaves the connection open to
a man-in-the-middle who could capture the mailbox password. Fix: set the property to `true`, or
move to Angus Mail, where it is the default. MAIL-010 could state that the server identity is
verified.
