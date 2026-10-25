---
title: User registration app with its own backend
kind: idea
area: persistence
added: 2026-10-25
---

Today the only way to define a user is the mock user (USER-030/031: one user, one mailbox, scan
positions reset on every restart) or writing entities by hand in the Firestore console. Users
should register themselves through a frontend app backed by a separate registration backend; the
mail producer stays a reader of the user storage and does not expose an API.

To settle in its plan: the backend's API (register, edit mailboxes and folders, delete), who
encrypts mailbox passwords (the backend needs the AES key, SECRET-004), authentication of users,
how editing a mailbox keeps the folders' scan positions, and exposure through the gateway. The
frontend waits for `frontend-design-system.md`. Note that the dev Firestore database is created
by `up` and destroyed by `down` (DEPLOY-010), so registered users do not survive `down`.
Specify it in a new spec and update `docs/agents/specs/user-configuration.md` (its "Out of scope"
item about a user interface) and `docs/setup/keys-and-users.md`.
