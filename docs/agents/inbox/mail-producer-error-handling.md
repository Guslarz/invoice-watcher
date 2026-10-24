---
title: Error handling in the mail scan and DAO layers
kind: bug
area: mail-scanning
added: 2026-10-24
---

- A folder failure is logged twice with a stack trace: `MailProducerTaskImpl.java:62-65` logs and
  rethrows, `:51-52` logs again with the whole `MailScanConfig` record, including the username.
- Only the per-mailbox block is guarded (`catch (Exception e)` at `:51`, which also catches
  `close()` failures); an exception in `processUser` or `user.scanConfigs()` stops the run for all
  remaining users.
- A message without a `From` header (`MailConnectionImpl.java:79`, `getFrom()[0]`) or without a sent
  date (`:67`, used inside the binary search) throws, so the folder is never scanned past it.
  MAIL-030 does not say what to do with such messages.
- A missing required property fails at startup with a bare `NullPointerException` from `Map.of`:
  `KafkaEventProducerAutoConfiguration.java:41-45` (`bootstrapServers`),
  `MailProducerDaoJpaAutoConfiguration.java:71-74` (`ddlAuto`, `dialect`). Validate the properties
  (`@Validated` + `@NotNull`) instead.
- The two DAO backends differ: a missing folder is silently ignored in Firestore
  (`FolderConfigDaoServiceFirestoreImpl.java:19`) but throws in JPA
  (`FolderConfigDaoServiceJpaImpl.java:22`, also an NPE on a null id); `upsertUserByName` is
  `@Transactional` in JPA, delete-then-save without a transaction in Firestore
  (`UserDaoServiceFirestoreImpl.java:48-56`). `FirestoreMapper.java:32` streams `getScanConfigs()`
  without the null check that `deleteUserByName` has.

User-visible behavior (messages without sender or date) needs a spec decision first (Protocol plan);
the rest are fixes.
