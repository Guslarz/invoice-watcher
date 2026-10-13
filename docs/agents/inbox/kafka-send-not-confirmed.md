---
title: Kafka delivery is not confirmed before moving the scan timestamp
kind: bug
area: events
added: 2026-10-13
---

Related to EVENT-011. `KafkaEventProducer.send` ignores the future returned by
`KafkaTemplate.send`, so a failed delivery is neither logged nor retried and the folder's scan
timestamp still moves forward — the message is lost. Wait for the result (or flush and check)
before `updateScanTimestamp`, and log failures.
