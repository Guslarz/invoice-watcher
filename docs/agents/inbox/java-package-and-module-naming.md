---
title: Make module, package and class naming rules consistent
kind: tech-debt
area: build
added: 2026-10-24
---

Naming follows several rules at once; pick one and write it into `docs/agents/guidelines/modules.md`.

- Module path → package: `apps/mail-producer-app` → `mailproducer.app`, but
  `apps/secret-helper-app` → `secret.helper.app`; `kafka-impl`/`jpa-impl`/`firestore-impl` drop
  `-impl` (`eventproducer.kafka`, `dao.jpa`), `mail-connector/impl` keeps it (`mailconnector.impl`).
- Module names: `libs/secret/aes` is an implementation without `-impl`; `libs/mail-connector/impl`
  is IMAP-specific without a technology prefix; `libs/util/aes` and `libs/secret/aes` share the
  leaf name `aes`.
- Implementation classes: `KafkaEventProducer` (prefix, no `Impl`), `SecretAesImpl` /
  `SecretFactoryAesImpl` (infix + `Impl`), `UserDaoServiceJpaImpl` (suffix + `Impl`),
  `MailConnectionImpl` / `MailProducerTaskImpl` (bare `Impl`).
- DAO implementations differ: Firestore `UserDocumentRepository` + one `FirestoreMapper`
  (`toUser`/`toUserDocument`) + public bean names `daoFirestore...`; JPA `UserRepository` (only
  this one has `@Repository`) + one mapper per type with overloaded `convert` + package-private
  bean names `mailProducerDaoJpa...`.
- Constants: `Prefix`, `PasswordMode` (UpperCamel) vs `IMAP_PROTOCOL`
  (`MailConnectionFactoryImpl.java:24`) and test constants `FOLDER`, `NOW`; Checkstyle has no
  `ConstantName` rule. `AesUtil.RandomGenerator` (`AesUtil.java:18`, public, used by
  `GenerateKeyCommand`) shadows the JDK type `java.util.random.RandomGenerator`.
- Visitors: `MailCredentialsVisitor.visitBasicMailCredentials(BasicAuthMailCredentials)` lacks
  "Auth" (the entity visitor has `visitBasicAuthMailCredentialsEntity`); the visitor parameter of
  `accept` is named `converter` (`MailCredentials.java:5`).

Package renames are refactors (Protocol lightweight); renaming a module also renames its project
path and the generated reference.
