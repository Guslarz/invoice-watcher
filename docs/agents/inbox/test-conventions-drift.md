---
title: Bring existing tests in line with the test conventions
kind: tech-debt
area: build
added: 2026-10-24
---

Against `docs/agents/guidelines/code.md`:
- `BinarySearchEngineTest` is in package `...mailconnector.impl.binarysearch`, the class it tests in
  `...mailconnector.impl.util.binarysearch`.
- `KafkaEventProducerTest` (lines 33, 45) uses a combined `// when / then`.
- Method names mix `should...` (`BinarySearchEngineTest`, `MailConnectionImplTest`) and
  third-person (`movesScanTimestampWhenAllEventsConfirmed`, `returnsWhenConfirmed`); the guideline
  does not say which.
- Clause IDs: `AesUtilTest.testEncryptAndDecrypt` has none;
  `MailConnectionImplTest.shouldReturnNothingForEmptyFolder` covers MAIL-022 without naming it.
- Nullness annotations are mixed: JSR-305 `javax.annotation.Nullable` (`SecretConverter`,
  `SecretKeyConverter`) vs jspecify (`SecretHelperRunner`). Pick jspecify and add it to the
  guideline.
