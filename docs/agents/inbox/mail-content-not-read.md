---
title: Message content is not read
kind: bug
area: mail-scanning
added: 2026-10-13
---

Violates MAIL-030. `MailConnectionImpl.toMessage` sets sender, title and date but not `content`,
so every event has `content: null` (EVENT-004). Decide what content means for multipart
messages (text/plain part, HTML converted to text, attachments' names) before implementing.
