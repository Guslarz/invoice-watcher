---
id: ADR-0006
status: accepted
date: 2026-10-13
---

# ADR-0006: Keep secrets out of the repository

## Context

InvoiceWatcher works with credentials to external systems: mailbox passwords, the AES key that
encrypts them, the GitHub token, registry credentials and the Cloudflare API token. Everything in
the repository is readable by anyone with access to it, stays in git history after it is
removed, and the repository is meant to become public. A secret committed once has to be treated
as leaked and rotated.

## Decision

Never commit a secret to the repository — not in code, configuration, manifests, `.env` files,
Terraform variables, tests or documentation. A secret is any password, token, key or other value
that grants access to an external system or protects sensitive data.

Secrets reach the system only from outside the repository:
- GitHub Actions read them from repository secrets.
- In the cluster they come from Google Secret Manager through external-secrets.
- Google Cloud access uses workload identity, so no service account keys exist at all.
- Locally they live in git-ignored files (`application-local.yml`) or the developer's environment.

Encrypted mailbox passwords (see [secret encryption](../specs/secret-encryption.md)) are not
secrets in this sense and may be stored in configuration, but the key that decrypts them is.
Examples and tests use obviously fake values.

## Consequences

- Every new credential needs a Secret Manager secret (or GitHub secret) and a way to deliver it,
  which is more work than a value in a `.env` file.
- A secret found in the repository is a bug: remove it, rotate it, and move it to a secret store.
  The AES key in the dev overlay is such a case (`docs/agents/inbox/aes-key-in-repo.md`).
- In code, secrets are held as `Secret` from `libs/secret/api` and never logged
  (`docs/agents/guidelines/code.md`).
