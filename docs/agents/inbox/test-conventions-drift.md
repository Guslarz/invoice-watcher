---
title: Bring existing tests in line with the test conventions
kind: tech-debt
area: build
added: 2026-10-24
---

Against `docs/agents/guidelines/code.md`:
- `KafkaEventProducerTest` (lines 33, 45) uses a combined `// when / then`.
- Method names mix `should...` (`MailConnectionImplTest`) and
  third-person (`savesPositionOnceWhenAllEventsConfirmed`, `returnsWhenConfirmed`); the guideline
  does not say which.
- Clause IDs: `AesUtilTest.testEncryptAndDecrypt` has none.
- Nullness annotations are mixed: JSR-305 `javax.annotation.Nullable` (`SecretConverter`,
  `SecretKeyConverter`) vs jspecify (`SecretHelperRunner`). Pick jspecify and add it to the
  guideline.
