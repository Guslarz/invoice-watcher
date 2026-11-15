---
id: ADR-0016
status: accepted
date: 2026-11-12
---

# ADR-0016: Share user configuration storage and DAOs between applications

## Context and decision

Registration and scanning operate on the same users, mailboxes, folders and OAuth grants. A
second database with synchronization would add stale reads, deletion races and dual writes.

Use one authoritative database per environment and libs/dao/user-configuration/{api,firestore-impl,
jpa-impl} for both applications. Registration commands, scan queries/position updates and grant
updates have narrow interfaces; entity mappings and transaction semantics belong to each shared
implementation. Configuration versions and grant revisions are separate. Retain folder identities
and update progress conditionally so stale scans cannot recreate deleted data. JPA applications
share a physical database, including one H2 TCP database locally. Disposable schema initialization
is explicit; durable schema migration tooling is a separate follow-up.

This couples schema evolution across deployments. Use compatible additive changes and contract
checks against H2 and the Datastore emulator. Application-specific HTTP/security and scanning
logic stays outside shared persistence.
