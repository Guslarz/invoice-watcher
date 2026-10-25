---
title: Firestore upsertUserByName is not transactional
kind: tech-debt
area: persistence
added: 2026-10-25
---

`UserDaoServiceFirestoreImpl.upsertUserByName` deletes the user and its folders and then saves them
again without a transaction, while the JPA backend does the same inside `@Transactional`. A failure
between the two steps leaves the user missing (Firestore) instead of unchanged (JPA). Only the mock
user calls it, and no environment deploys the mock user, so it was left as is. Making it atomic
needs a Datastore transaction manager next to JPA's, so the two do not compete for the
`transactionManager` bean.
