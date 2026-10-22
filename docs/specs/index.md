# Specifications

Each specification describes one capability of InvoiceWatcher: what it does, in numbered
clauses, with acceptance criteria. Specifications are the source of truth — code and tests are
checked against them, and guides quote them.

| Spec | Capability | Status |
|------|------------|--------|
| [SPEC-MAIL](mail-scanning.md) | Mail scanning — which mailboxes and messages are read, and when | implemented, with gaps |
| [SPEC-EVENT](event-publishing.md) | Event publishing — what is sent to Kafka for each message | implemented |
| [SPEC-USER](user-configuration.md) | User configuration — users, mailboxes, folders and where they are stored | implemented |
| [SPEC-SECRET](secret-encryption.md) | Secret encryption — mailbox passwords and the `secret-helper` tool | implemented |
| [SPEC-DEPLOY](deployment.md) | Deployment — creating an environment and running the application on GKE | implemented |
| [SPEC-RELEASE](build-and-release.md) | Build and release — checks, snapshot images and releases | implemented |

A clause marked *(gap: …)* describes intended behavior that the code does not meet yet; the
referenced note tracks the fix.
