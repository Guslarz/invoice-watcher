---
title: Binary search over folder messages uses 0-based indexes
kind: bug
area: mail-scanning
added: 2026-10-13
---

Violates MAIL-022. `MailConnectionImpl.findFirstIndexMatchingQuery` searches indexes
`0..count-1`, but IMAP message numbers are `1..count` (`Folder.getMessage(0)` throws
`IndexOutOfBoundsException`). Consequences:
- empty folder → `getMessages(0, 0)` fails;
- no message newer than the scan timestamp → result is `count`, so the newest (already processed)
  message is published again on every scan;
- all messages newer than the timestamp → the search reaches `getMessage(0)` and fails.

Fix: search `1..count`, return "none" when nothing matches, and add tests with an in-memory
folder for these three cases. `BinarySearchEngineTest` covers only the engine.
