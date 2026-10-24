---
title: mail-producer-app ships every DAO backend and the mock user
kind: idea
area: build
added: 2026-10-24
---

`apps/mail-producer-app/build.gradle.kts:11-13` puts `mailProducerDaoJpaImpl` (with H2 and the
Cloud SQL starter), `mailProducerDaoFirestoreImpl` and `mailProducerMockUser` on the production
classpath. That is why `application.yml:2-10` excludes `DataSourceAutoConfiguration` and disables
`spring.cloud.gcp.sql` / `datastore`. The `mode` switch needs both backends on the classpath, but
H2 and the mock user are for local runs only.

Options: keep the switch but move H2 and the mock user to a local-only variant of the app, or build
per-backend images. Also, `MailProducerMockUserInitializer` writes to the database in
`@PostConstruct` while the context is starting (an `ApplicationRunner` or `ApplicationReadyEvent`
listener is safer) and uses `ZonedDateTime.now()` in the system zone.
