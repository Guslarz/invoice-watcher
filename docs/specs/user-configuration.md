---
id: SPEC-USER
title: User configuration
status: implemented
audience: [user, operator, developer]
modules:
  - libs/mail-producer/dao/api
  - libs/mail-producer/dao/firestore-impl
  - libs/mail-producer/dao/jpa-impl
  - libs/mail-producer/mock/user
---

# User configuration

## Purpose

InvoiceWatcher needs to know whose mailboxes to watch and how to reach them. This configuration
is stored in a database and read at the start of every scan, so changes take effect without a
restart.

## Behavior

### Model

<!-- --8<-- [start:model] -->
- **USER-001** A **user** has a unique name — their e-mail address — and one or more **mailbox
  configurations**.
- **USER-002** A mailbox configuration has a server (IMAP host and port), credentials (username and
  encrypted password, see [secret encryption](secret-encryption.md)) and one or more **folders**.
- **USER-003** A folder has a name as seen by the mail server (e.g. `INBOX`, `Invoices`,
  `INBOX/Bills`) and a scan timestamp (see MAIL-020).
<!-- --8<-- [end:model] -->
- **USER-004** Configuration is read anew at the beginning of every scan; the only value
  InvoiceWatcher itself writes during normal operation is the folder's scan timestamp.

### Storage

- **USER-010** The storage is selected with `invoice-watcher.mail-producer.dao.mode`:
  `firestore` (used in the deployed environments) or `jpa` (a relational database, used for local
  development with H2).
- **USER-011** In `firestore` mode, users are stored in Firestore in Datastore mode, kind
  `mail-producer-users` (one entity per user, keyed by name, with the mailbox configurations
  embedded), and folders in kind `mail-producer-folders` (one entity per folder, referenced from
  the mailbox configuration by id). The scan timestamp is stored as ISO-8601 text.
- **USER-012** In `firestore` mode, setting `...dao.firestore.host` connects to a Datastore
  emulator without credentials; otherwise the application uses Google default credentials and the
  database `...dao.firestore.database-id`.
- **USER-013** In `jpa` mode the connection is configured with `...dao.jpa.data-source.basic.*`
  (URL, username, password, driver), and the schema handling and dialect with `...dao.jpa.ddl-auto`
  and `...dao.jpa.dialect`.

### Mock user

<!-- --8<-- [start:mock-user] -->
- **USER-030** When `invoice-watcher.mail-producer.mock.user.enabled=true`, the application
  creates or replaces, at startup, one user from properties: name and username `...email`,
  server `...host` and `...port` (default 993), password `...password` (encrypted value) and the
  folders listed in `...folders`.
- **USER-031** Every replace resets the mock user's folders: their scan timestamps are set to ten
  days before the application start. Each restart therefore picks up the last ten days of mail
  again.
<!-- --8<-- [end:mock-user] -->

## Acceptance

- **USER-004** Given a running application, when a folder is added to a user in the database,
  then the next scan scans that folder without a restart.
- **USER-030** Given the mock user properties for `jan@example.com` with folders `INBOX` and
  `Invoices`, when the application starts, then the database contains exactly one user
  `jan@example.com` with these two folders.
- **USER-031** Given the mock user already exists with a scan timestamp from yesterday, when the
  application restarts, then the scan timestamp is ten days before the restart.

## Configuration

Prefix `invoice-watcher.mail-producer`:

| Property | Default | Meaning |
|----------|---------|---------|
| `dao.mode` | — | `firestore` or `jpa` |
| `dao.firestore.database-id` | — | Firestore database id |
| `dao.firestore.host` | — | Emulator `host:port`; when set, no credentials are used |
| `dao.jpa.data-source.basic.{url,username,password,driver}` | — | JDBC connection |
| `dao.jpa.ddl-auto`, `dao.jpa.dialect` | — | Hibernate schema handling and dialect |
| `mock.user.enabled` | `false` | Create the mock user at startup |
| `mock.user.{email,host,port,password,folders}` | port `993` | Mock user definition; `password` is encrypted |

## Out of scope

- A user interface or API for managing users — today users are created via the mock user or
  directly in the database.
- Several users sharing a mailbox.
