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

- **SECRET-010** `secret-helper generate-key` prints a new random 256-bit AES key, Base64-encoded,
  made with a cryptographically secure random generator. It asks for nothing.
- **SECRET-011** `secret-helper encrypt` asks for the key and the value and prints the encrypted
  value.
- **SECRET-012** `secret-helper decrypt` asks for the key and an encrypted value and prints the
  original value.
- **SECRET-013** All input is read interactively from standard input, so keys and passwords do not
  end up in the shell history. The tool exits with status 0 on success.
- **SECRET-014** Without a command, or with an unknown command or option, the tool prints its
  usage on standard error and exits with status 2.
- **SECRET-015** When a command fails — a wrong key or a tampered value in `decrypt`, an invalid
  key, input ending early — the tool prints a one-line message on standard error, without a stack
  trace and without any plaintext, and exits with status 1.

## Acceptance

- **SECRET-001** Given a key, when `encrypt` is run twice with the same password, then the two
  printed values differ and both decrypt to the password.
- **SECRET-005** Given a value encrypted with key A, when `decrypt` is run with key B, then the
  tool fails and prints no plaintext.
- **SECRET-010** Given nothing on standard input, when `generate-key` runs twice, then it prints
  two different Base64 values that decode to 32 bytes and are accepted by `encrypt`.
- **SECRET-012** Given a key from `generate-key` and a value from `encrypt`, when `decrypt` is run
  with them, then it prints the original value.
- **SECRET-014** Given no arguments, when the tool runs, then its usage is printed on standard
  error and the exit status is 2. The same for `generate-key --mode password` (an unknown option).
- **SECRET-015** Given a value encrypted with key A, when `decrypt` runs with key B, then standard
  error has one line, standard output has no plaintext and the exit status is 1.
- **SECRET-015** Given empty standard input, when `encrypt` runs, then standard error has one line
  and the exit status is 1.

## Configuration

Properties, their types, defaults and meaning are listed in the generated reference:

- [`invoice-watcher.secret.aes`](../../reference/configuration.md#invoice-watcher-secret-aes)

## Out of scope

- Key rotation and re-encryption of stored values.
- Integration with a key management service.
