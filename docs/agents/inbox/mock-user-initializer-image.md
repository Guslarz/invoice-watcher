---
title: Mock user initializer image that registers users through the registration API
kind: idea
area: deployment
added: 2026-10-25
---

Once the registration backend exists (`user-registration-app.md`), replace the `mock/user` module
inside the mail producer with a separate Docker image (probably a small Spring Boot app) that, on
startup, registers the users from its configuration through the registration backend's API. It is
meant for tests and for filling the dev environment after `up` (its Firestore database does not
survive `down`), so it exercises the same path real users take instead of writing to the storage
directly. Then remove `libs/mail-producer/mock/user`, USER-030/031 and the mock user section of
`docs/setup/keys-and-users.md`.
