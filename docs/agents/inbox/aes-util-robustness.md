---
title: Make AesUtil independent of the default charset and validate input
kind: tech-debt
area: secrets
added: 2026-10-24
---

`libs/util/aes/.../AesUtil.java`:
- `input.getBytes()` (line ~37) and `new String(result)` (~47) use the default charset; make it
  explicit (`StandardCharsets.UTF_8`). The default is UTF-8 since JDK 18, so nothing breaks today.
- `decrypt` splits with `substring(0, 16)` and the comment `// 16 characters for 12 bytes`; name the
  constants (IV length and its Base64 length). An input shorter than 16 characters throws
  `StringIndexOutOfBoundsException` instead of a clear error (relevant to SECRET-005).
- SECRET-001 (fresh IV per encryption) and SECRET-005 (wrong key fails) have no tests; `AesUtilTest`
  covers only the round trip.
