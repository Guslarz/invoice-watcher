---
title: First scan of a folder without a scan timestamp fails
kind: bug
area: mail-scanning
added: 2026-10-13
---

Violates MAIL-023. A folder whose `scanTimestamp` is null (any folder not created by the mock
user) makes `DateUtil.toDate(query.dateFrom())` throw `NullPointerException`; the folder and the
remaining folders of that mailbox are skipped forever. Decide what the first scan should pick up
(all mail, or only mail from "now" on) and update MAIL-023 accordingly.
