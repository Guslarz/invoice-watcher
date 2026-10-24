---
title: Declare the dependencies modules actually use
kind: tech-debt
area: build
added: 2026-10-24
---

- Redundant: `api(projects.libs.secretApi)` in `libs/mail-connector/impl/build.gradle.kts:9` and
  `libs/mail-producer/mock/user/build.gradle.kts:9` — it already comes through the api modules.
  `jackson-core` in `libs/event-producer/kafka-impl/build.gradle.kts:12` comes with `jackson-databind`
  (and neither is imported directly).
- Undeclared, used through transitive dependencies:
  `org.apache.commons.lang3.StringUtils` in `MailProducerDaoFirestoreAutoConfiguration`;
  `jakarta.annotation.PostConstruct` in firestore-impl and jpa-impl (mock-user and scheduler-impl
  declare `libs.jakarta.annotations`); `javax.annotation.Nullable` (JSR-305) in `libs/secret/aes`
  and `SuppressFBWarnings` in `secret-helper-app`, which appear to come from freefair-lombok adding
  `spotbugs-annotations` when SpotBugs is applied (verify with `dependencyInsight`).
- Verify: no module declares `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`, which
  Gradle 9 no longer adds implicitly.

A dependency analysis plugin (e.g. `com.autonomousapps.dependency-analysis`) would keep this honest.
