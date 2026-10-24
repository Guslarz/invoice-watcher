---
title: Provide a way to add users to an environment
kind: idea
area: persistence
added: 2026-10-13
---

The only supported way to define a user is the mock user from properties (USER-030), which is a
single user with one mailbox whose scan positions reset on every restart (USER-031). Adding real
users to the dev environment means writing nested entities (`mail-producer-users` with embedded
mailbox configurations, `mail-producer-folders`) by hand in the Firestore console. The dev
environment has no users yet; filling it is still to be done.

Decide how users are added (an admin CLI command in `secret-helper` or a new tool, an import from
a file, a small API) and specify it in `docs/agents/specs/user-configuration.md` (the "Out of scope" item
about a user interface). Then update `docs/setup/keys-and-users.md`.
