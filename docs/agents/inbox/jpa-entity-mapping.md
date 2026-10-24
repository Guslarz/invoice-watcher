---
title: Clean up the JPA entity mapping
kind: tech-debt
area: persistence
added: 2026-10-24
---

- `MailCredentialsEntity` combines `@DiscriminatorColumn(name = "type")` with
  `InheritanceType.TABLE_PER_CLASS`; `BasicAuthMailCredentialsEntity` has `@DiscriminatorValue`.
  With table-per-class the discriminator is ignored (verify with the generated schema).
- `UserDaoServiceJpaImpl.java:45-50` saves credentials, folders, scan configs and the user one by
  one — a hand-written cascade, because relations only cascade `REMOVE`. `CascadeType.PERSIST`
  (or `ALL` + orphan removal) would replace it.
- `MailServerEntity.port` is `Integer`, the domain `MailServer.port` is `int`: a null column fails
  on unboxing.
