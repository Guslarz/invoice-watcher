---
title: Remove unused code in libs
kind: tech-debt
area: persistence
added: 2026-10-24
---

Found by grep, no callers or readers:
- `UserDaoService.add(User)` and both implementations. The JPA one (`UserDaoServiceJpaImpl.java:35`)
  would also save only the root, as `UserEntity.scanConfigs` cascades only `REMOVE` (verify).
- `MailProcessorContext.scanConfig` is set but never read.
- `MailCredentialsVisitor.visit(MailCredentials)` duplicates `BaseMailCredentialsVisitor.visit`;
  `FirestoreMapper.java:114` calls `accept` directly. `MailCredentialsMapper` both extends
  `BaseMailCredentialsVisitor<MailCredentialsEntity>` and implements
  `MailCredentialsEntityVisitor<MailCredentials>` — two `visit` overloads in opposite directions.
- `SecretAesProperties` (comment "Only used to generate metadata file") and `SecretKeyConverter`
  decode the key into a record nobody injects; `SecretFactoryAesImpl` reads the same key again via
  `@Value`. `SecretFactoryAesImpl` is annotated `@ConfigurationPropertiesBinding` without being a
  converter.
- `MailProducerMockUserProperties.enabled` — only `@ConditionalOnBooleanProperty` uses the key.
- `@RequiredArgsConstructor` on `SecretHelperRunner`, which has no fields.
