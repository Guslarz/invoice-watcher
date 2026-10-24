---
title: Validate configuration properties at startup
kind: idea
area: build
added: 2026-10-24
---

No `@ConfigurationProperties` record is validated: there is no `@Validated`, no `jakarta.validation`
constraint and no `spring-boot-starter-validation` on the classpath. A missing or malformed value
binds as `null`/default and fails later, at first use, with an unrelated error. Examples: the
Javadoc of `SecretAesProperties.key` says "required" but nothing enforces it;
`KafkaEventProducerProperties.bootstrapServers`, `MailProducerMockUserProperties.host`/`email` and
the JPA `ddlAuto`/`dialect` can be absent; `MailProducerSchedulerProperties.rate` can be zero or
negative.

Idea: add the validation starter (Boot-managed version) and annotate the records with
`@Validated` + constraints (`@NotNull`, `@NotBlank`, `@Positive`/`@DurationMin`, `@Valid` on nested
records), so a misconfigured app fails at startup with a message naming the property. Decide where
the dependency lives — `api` modules own the records but carry only `libs.spring.boot` today
(`docs/agents/guidelines/modules.md`). Constraints could also be shown in the generated
configuration reference.

Startup behavior and the module guideline change, so this goes through Protocol plan. Overlaps the
JPA bullet of `mail-producer-error-handling.md` (`@Validated` + `@NotNull` for `ddlAuto`, `dialect`);
fold that bullet into this change.
