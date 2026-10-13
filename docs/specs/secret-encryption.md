---
id: SPEC-SECRET
title: Secret encryption
status: implemented
audience: [user, operator, developer]
modules:
  - libs/secret/api
  - libs/secret/aes
  - libs/util/aes
  - apps/secret-helper-app
---

# Secret encryption

## Purpose

Mailbox passwords must be stored by InvoiceWatcher, but must not be readable by anyone who sees
the stored configuration. They are kept encrypted with a key known only to the running
application, and the `secret-helper` command-line tool lets an operator produce the key and the
encrypted values.

## Behavior

### Encryption

- **SECRET-001** Secrets are encrypted with AES-256 in GCM mode, with a fresh random 12-byte IV
  for every encryption. The same password encrypted twice gives two different values.
- **SECRET-002** An encrypted value is a single text: the Base64 IV (16 characters) followed by
  the Base64 ciphertext. It can be stored anywhere a string can.
- **SECRET-003** The key is a Base64-encoded 256-bit AES key, given to the application in
  `invoice-watcher.secret.aes.key`. The application fails to start without it.
- **SECRET-004** Stored passwords (and the mock user's password, see USER-030) are decrypted only
  when a mailbox connection is made, and never logged.
- **SECRET-005** A value encrypted with one key cannot be decrypted with another; decryption with
  a wrong key fails instead of returning garbage.

### `secret-helper` tool

<!-- --8<-- [start:secret-helper] -->
- **SECRET-010** `secret-helper generate-key` asks for a password and prints a new key derived
  from it (PBKDF2-HMAC-SHA256, 65 536 iterations, random salt). The salt is not shown, so the same
  password gives a different key every time — keep the printed key, not the password.
- **SECRET-011** `secret-helper encrypt` asks for the key and the value and prints the encrypted
  value.
- **SECRET-012** `secret-helper decrypt` asks for the key and an encrypted value and prints the
  original value.
- **SECRET-013** All input is read interactively from standard input, so keys and passwords do not
  end up in the shell history. The tool exits with status 0 on success.
<!-- --8<-- [end:secret-helper] -->

## Acceptance

- **SECRET-001** Given a key, when `encrypt` is run twice with the same password, then the two
  printed values differ and both decrypt to the password.
- **SECRET-005** Given a value encrypted with key A, when `decrypt` is run with key B, then the
  tool fails and prints no plaintext.
- **SECRET-012** Given a key from `generate-key` and a value from `encrypt`, when `decrypt` is run
  with them, then it prints the original value.

## Configuration

Properties, their types, defaults and meaning are listed in the generated reference:

- [`invoice-watcher.secret.aes`](../reference/configuration.md#invoice-watcher-secret-aes)

## Out of scope

- Key rotation and re-encryption of stored values.
- Integration with a key management service.
- `generate-key --mode random` is accepted but not implemented and fails.
