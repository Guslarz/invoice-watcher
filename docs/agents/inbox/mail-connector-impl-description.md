---
title: Stale description of the mail-connector impl module
kind: tech-debt
area: build
added: 2026-10-24
---

`libs/mail-connector/impl/build.gradle.kts` still describes the module as "IMAP mailbox connection
based on Jakarta Mail, with date-based message search". Since folders are scanned by IMAP UID
position (MAIL-020), it should read e.g. "IMAP mailbox connection based on Jakarta Mail, scanning
folders by UID". Left out of the scan-window change because build files were being changed in
parallel by the build-conventions cleanup.
