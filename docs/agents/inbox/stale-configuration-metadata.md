---
title: Incremental compile drops property descriptions from configuration metadata
kind: bug
area: build
added: 2026-10-22
---

After an incremental compile that recompiled only `MailProducerDaoFirestoreAutoConfiguration`
(not the `MailProducerDaoFirestoreProperties` record), the regenerated
`build/classes/java/main/META-INF/spring-configuration-metadata.json` of
`libs/mail-producer/dao/firestore-impl` kept the properties but lost their `description`s, and
`scripts/gen-reference.py` (run by `scripts/verify.sh`) failed with "property ... has no
description". A `clean` of the module fixed it. Consider making `docsReference` depend on a
non-incremental compile, or have `verify.sh` hint at `clean` when this error appears.
