---
id: ADR-0021
status: accepted
date: 2026-11-17
---

# ADR-0021: Publish database-backed events through a durable outbox

## Context

Saving a database outcome and sending its resulting event are separate operations. A crash between
those operations can lose output; a crash after broker confirmation can repeat publication. A
completion flag alone does not make those writes atomic. The processor foundation initially uses
Firestore in Datastore mode, but this failure window is independent of database technology.

## Decision

- When an accepted database outcome requires event publication, save both the outcome and its
  immutable pending publications in one database transaction behind the DAO API.
- Dispatch pending values independently of a fresh delivery of the input. Use stable result
  identities, tenant keys, accepted timestamps and serialized content across publication retries.
- Mark publication confirmed after broker acknowledgment and clear its stored sensitive body.
  Retain identity/disposition metadata needed for duplicate detection and recovery.
- Accept at-least-once event delivery after uncertain acknowledgment. Require downstream consumers
  to apply stable result identities idempotently; do not claim a distributed database/Kafka
  transaction or exactly-once external service calls.

## Consequences

Accepted outcomes do not lose required events across restart. Persistence writes, recovery queries
and temporary sensitive-payload storage add cost. Consumers must tolerate repeated identities.
This decision covers database-backed publication; it does not prescribe all processing runtimes,
quarantine storage, business matching or a permanent raw-evidence archive.
