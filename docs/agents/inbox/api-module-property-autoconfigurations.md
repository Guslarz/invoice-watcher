---
title: Question property-only auto-configurations in api modules
kind: idea
area: build
added: 2026-10-24
---

`EventProducerAutoConfiguration` and `MailProducerDaoAutoConfiguration` (in the `api` modules)
only `@EnableConfigurationProperties` for records that no bean injects — implementations use just
`Constants.Prefix` / `Constants.Mode` in `@ConditionalOnProperty`. The guideline allows such
auto-configurations, but they may be unnecessary: the configuration processor writes metadata for
every `@ConfigurationProperties` type regardless of registration (verify against the generated
reference). If they go, update `docs/agents/guidelines/modules.md`.

Related inconsistencies:
- Spring dependency of api modules: `event-producer/api` uses `implementation(libs.spring.boot.starter)`,
  `mail-producer/dao/api` uses `spring-boot-autoconfigure`, `mail-connector/api` none.
- `KafkaEventProducerProperties` hardcodes `"invoice-watcher.event-producer.kafka"` instead of
  building it from `EventProducerProperties.Constants.Prefix`; the mock user writes
  `"invoice-watcher.mail-producer.mock.user"` twice (auto-configuration and properties).
