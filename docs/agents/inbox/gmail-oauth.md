---
title: Research Gmail OAuth instead of basic authentication
kind: idea
area: mail-scanning
added: 2026-10-25
---

Mailboxes are reached only with a username and password (MAIL-011); for Gmail that means the user
creates an app password, which needs two-factor authentication and is easy to get wrong. Research
letting users connect a Gmail mailbox through OAuth instead: IMAP with SASL `XOAUTH2` (Angus Mail
supports it, the access token is passed instead of a password) or the Gmail API. To settle: the
scope (`https://mail.google.com/` for IMAP, or `gmail.readonly` for the API — both are restricted
scopes, so a public app needs Google's verification and a yearly security assessment, and in
testing mode refresh tokens expire after 7 days and only listed test users can consent), where the
consent flow runs (the registration backend, `user-registration-app.md`), storing the refresh
token encrypted (SECRET-004) and refreshing access tokens before a scan, and a new credentials type
next to `BasicAuthMailCredentials`. OAuth is listed as out of scope in
`docs/agents/specs/mail-scanning.md`; doing it changes MAIL-011 and USER-002. Consider Microsoft
(Outlook.com / Microsoft 365) at the same time, as it also pushes towards OAuth for IMAP.
