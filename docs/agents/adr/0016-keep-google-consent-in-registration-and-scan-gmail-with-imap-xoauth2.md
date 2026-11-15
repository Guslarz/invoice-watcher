---
id: ADR-0016
status: accepted
date: 2026-11-12
---

# ADR-0016: Keep Google consent in registration and scan Gmail with IMAP XOAUTH2

## Context and decision

Google is initially the only configured OpenID provider. Login requests identity scopes; Gmail
connection explicitly requests offline mail permission. Bind ownership to issuer and subject and
reject account switching. Keep codes, client credentials and tokens in the backend; encrypt stored
refresh tokens with the same AES key used by scanning.

Use IMAP XOAUTH2 to preserve existing folder/UID semantics and message events. Gmail requires the
full-mail scope even though scanning remains read-only. A Gmail API reader could request narrower
permissions but would require a different progress model; it is deferred. Preserve password
credentials and IMAP code for manual/mock users, while registration accepts Gmail OAuth only.

When a grant expires, sign-in automatically continues through consent; no dedicated reconnection
screen is required. A valid grant does not request consent again. Refusing consent preserves the
session and configuration. External / Testing is the initial audience; public verification is a
separate operational prerequisite. Revocation is a provider operation that cannot be atomic with
local deletion; provider failure must not retain deleted local credentials.
