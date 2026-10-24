---
title: Clarify secret-helper generate-key modes
kind: idea
area: secrets
added: 2026-10-13
---

`generate-key` (SECRET-010) derives the key from a password with a random salt that is thrown
away, so the password cannot reproduce the key — the mode behaves like a random key generator
with extra steps. `--mode random` is not implemented. Either implement `random` (SecureRandom
256-bit) and make it the default, or print the salt / accept it as input so that password mode is
reproducible.

`--mode` is a `String`; an unknown value throws `IllegalArgumentException` without a message
(`GenerateKeyCommand.java:39`). An enum option would let picocli validate it and list the
candidates in `--help`.
