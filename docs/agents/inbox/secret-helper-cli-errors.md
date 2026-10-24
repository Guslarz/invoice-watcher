---
title: secret-helper prints nothing without a command and stack traces on errors
kind: bug
area: secrets
added: 2026-10-24
---

- `secret-helper` without a command exits with status 1 and prints nothing:
  `SecretHelperCommand.call()` just returns `1`. It should print usage and exit with 2, like other
  usage errors.
- A wrong key or tampered value in `decrypt`, end of input in `Scanner.nextLine`, and an unknown or
  unimplemented `--mode` end in a full stack trace (picocli's default exception handler). A short
  message on stderr would do: set an `IExecutionExceptionHandler` in `SecretHelperRunner`.
- `GenerateKeyCommand` prints with `System.out.println`, the other commands with `IO.println`.
- Every argument, including `--spring.*`, is passed to picocli, so Spring arguments are rejected as
  unmatched.

Related: `secret-helper-generate-key-modes.md`.
