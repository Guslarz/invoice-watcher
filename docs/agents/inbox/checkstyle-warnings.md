---
title: Fix Checkstyle warnings and make them fail the build
kind: tech-debt
area: build
added: 2026-10-13
---

`./gradlew analyze` reports 65 Checkstyle warnings, all in
`libs/mail-producer/dao/firestore-impl/src/main/java/com/guslarz/invoicewatcher/mailproducer/dao/firestore/MailProducerDaoFirestoreAutoConfiguration.java`:
the file is indented with 4/8 spaces instead of Google style's 2/4 (`Indentation`), and uses a
star import of `com.google.cloud.spring.data.datastore.core.convert.*` (`AvoidStarImport`).
Other files of the module (e.g. `MailProducerDaoFirestoreProperties`) use the same 8-space
continuation indent; check them when reformatting.

The build stays green because `config/checkstyle/checkstyle.xml` reports violations with severity
`warning` (`org.checkstyle.google.severity`), so new violations slip in unnoticed. After
reformatting, consider setting the severity to `error` (or `maxWarnings = 0` in the Checkstyle
Gradle configuration) so that `./gradlew analyze` fails on any violation.
