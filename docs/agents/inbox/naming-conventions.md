---
title: Make naming consistent and write the rules down
kind: tech-debt
area: build
added: 2026-10-24
status: ready
size: L
blocked-by: []
---

Naming follows several rules at once. Pick one rule per item, write them into
`docs/agents/guidelines/modules.md` (modules, packages, classes) and `docs/agents/guidelines/code.md`
(constants), enforce what Checkstyle can, then rename. Split into several pull requests: class and
package renames are refactors (Protocol lightweight); renaming a module also renames its project
path and the generated reference; moving a configuration property needs Protocol plan.

**Modules and packages**
- Module path → package: `apps/mail-producer-app` → `mailproducer.app`, but
  `apps/secret-helper-app` → `secret.helper.app`; `kafka-impl`/`jpa-impl`/`firestore-impl` drop
  `-impl` (`eventproducer.kafka`, `dao.jpa`), `mail-connector/impl` keeps it (`mailconnector.impl`).
- Module names: `libs/secret/aes` is an implementation without `-impl`; `libs/mail-connector/impl`
  is IMAP-specific without a technology prefix; `libs/util/aes` and `libs/secret/aes` share the
  leaf name `aes`.
- `MailProducerTask` lives alone in `libs/mail-producer/scheduler/api`, while its implementation is
  in `libs/mail-producer/task`, which has no api/impl split.

**Classes**
- Implementation classes: `KafkaEventProducer` (prefix, no `Impl`), `SecretAesImpl` /
  `SecretFactoryAesImpl` (infix + `Impl`), `UserDaoServiceJpaImpl` (suffix + `Impl`),
  `MailConnectionImpl` / `MailProducerTaskImpl` (bare `Impl`).
- DAO implementations differ: Firestore `UserDocumentRepository` + one `FirestoreMapper`
  (`toUser`/`toUserDocument`) + public bean names `daoFirestore...`; JPA `UserRepository` (only
  this one has `@Repository`) + one mapper per type with overloaded `convert` + package-private
  bean names `mailProducerDaoJpa...`.
- Visitors: `MailCredentialsVisitor.visitBasicMailCredentials(BasicAuthMailCredentials)` lacks
  "Auth" (the entity visitor has `visitBasicAuthMailCredentialsEntity`); the visitor parameter of
  `accept` is named `converter` (`MailCredentials.java:5`).
- `MailConnectionProperties` (mail-connector api) is a builder record, not
  `@ConfigurationProperties`, despite the suffix.

**Spec vocabulary** (one concept, several names)
- The scan is a "task" (`MailProducerTask`), a "scheduler" module and a "processor"
  (`MailProcessorService`, `MailProcessorContext`); the specs say "scan" (MAIL-002) and "publish"
  (EVENT-011).
- The spec's "mailbox configuration" (USER-001/002) is `MailScanConfig` / `scanConfigs`.
- "Email" (`MailConnection.getEmailMessages`, `processEmailMessages`) next to "Mail" everywhere else.
- Domain records (`User`, `MailScanConfig`, `MailServer`, `FolderConfig`) are in
  `...dao.api.entity.user`, although they are not entities and not all about users.
- The event topic is `invoice-watcher.mail-producer.task.topic` — an event-publishing setting under
  the task prefix (moving it is a configuration change: Protocol plan).

**Static final fields**
- `static final` constants now use `UPPER_SNAKE_CASE` (Google Java style). `ConstantName` and
  `StaticVariableName` are enabled in `config/checkstyle/checkstyle.xml`, and
  `docs/agents/guidelines/code.md` documents the rule.
- Mutable static final references use `lowerCamelCase`, with field-local Checkstyle suppressions
  explaining why they are not constants. `AesUtil.randomGenerator` is private; AES IV generation
  and `GenerateKeyCommand` key generation use `AesUtil.fillRandomBytes(byte[])`. The shared
  `SecureRandom` instance, ownership and algorithm are unchanged. Remaining stages in this note
  are not addressed.
