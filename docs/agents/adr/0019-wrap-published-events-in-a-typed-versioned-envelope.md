---
id: ADR-0019
status: accepted
date: 2026-11-16
---

# ADR-0019: Wrap published events in a typed, versioned envelope

## Context

Mail ingestion originally published the message object directly as flat JSON. The
source-identity prerequisite introduces an explicitly versioned contract, while ADR-0010
anticipates invoice recognition, payment recognition and matching events. Repeating transport
metadata in every domain payload couples publishing conventions to each message model.
Java class names in serializer headers also couple consumers to producer implementation names.

## Decision

- Wrap published values in a shared typed envelope containing `eventType`, `schemaVersion`
  and `payload`. Keep the Kafka key outside the value envelope.
- Identify each complete contract by semantic event type and schema version. Version both
  envelope and payload together for that event type. Preserve meanings for additive changes;
  use a new version and explicit rollout for breaking changes.
- Keep payload models and source/business identity semantics specific to each capability.
  Share envelope validation and publishing without a central registry of Java payload classes.
- Omit Java class-name headers on new records. Consumers select a contract by its explicit
  type/version, tolerate unknown additive fields and report unsupported contracts explicitly.
- Accept the breaking transition from flat mail values to the envelope on the existing topic.
  Update consumers before producer rollout. Retain historical flat records without rewriting,
  dual publishing or inventing source identities. Replay readers must distinguish legacy
  records from versioned events.

## Consequences

Future event types reuse transport conventions without changing generic Kafka publishing.
Payload validation and business semantics remain local to their capability. Consumers depend
on an explicit wire contract rather than Java packages, and must handle legacy formats and
unsupported versions deliberately. This change provides no production consumer or recognition
pipeline; their contracts remain subsequent work.
