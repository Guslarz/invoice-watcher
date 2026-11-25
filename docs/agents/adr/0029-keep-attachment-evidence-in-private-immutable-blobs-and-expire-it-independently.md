---
id: ADR-0029
status: accepted
date: 2026-11-24
---

# ADR-0029: Keep attachment evidence in private immutable blobs and expire it independently

## Context

Invoice and payment recognition need replayable attachment bytes. Putting binaries in Kafka
multiplies sensitive content across ingestion, quarantine and replay records and couples document
sizes to broker limits. Reopening an IMAP message cannot guarantee historical evidence remains
available. Uploading outside Kafka creates an unavoidable publication failure window.

## Decision

- Store admitted untrusted PDF bytes in private GCS objects for deployed dev and publish only
  metadata and exact generation references. Local operation may explicitly select a private POSIX
  directory with atomic create-only publication, exclusive process ownership and opaque generations.
  Both implementations preserve immutable bytes, ownership and fixed deadlines across restart.
  Upload before publication; references contain no bearer credentials or absolute filesystem paths.
- Local filesystem cleanup runs at startup and periodically while running; reads enforce expiry
  even after an offline interval. This alternative makes no distributed-storage or backup promise.
  Capture metadata may independently use JPA/H2 with the same reservation and suppression semantics.
- Scope occurrence identities and ownership to the application user in user configuration.
  Repeated ingestion reuses one immutable object per occurrence/MIME path. Do not deduplicate
  equal bytes across messages or users. Verify existing bytes when reconciling uncertain uploads.
- Persist first-capture reservations and final manifests behind a capability DAO. Fence stale
  capture attempts, retain at-least-once event semantics, and do not claim a database/blob/Kafka
  transaction or a new publication outbox. Mail source availability remains necessary until handover.
- Expire evidence on a finite first-capture deadline. Success, failure and replay neither shorten
  nor extend it. Ambiguous publication never authorizes early orphan deletion; unpublished uploads
  expire by the same independent lifecycle as published evidence.
- Explicitly configure all application topic retentions. Cover the supported input retention and
  recovery margin before admitting publication. Stop visibly when that remaining lifetime is gone.
  A future quarantine/replay stage must revisit its full evidence window.
- Deny access at expiry, independently of asynchronous physical cleanup. Keep minimal suppression
  markers for the installation lifetime to prevent silent recreation; teardown deletes both bytes
  and metadata. Do not claim an audit archive or a guarantee beyond the installation lifetime.
- Define capture permissions in persistent project setup and bind them to the ephemeral bucket
  and producer workload. The role definition survives down/up without retaining attachment data.
- Treat raw evidence as untrusted. Application-user ownership checks are distinct from cloud
  workload authentication. This delivery includes no document parser, malware clearance or public
  downloads; consider malware scanning much later through the backlog.

## Consequences

Kafka payloads stay bounded without attachments, duplicate delivery does not duplicate stored
objects, and failed/unpublished captures cannot accumulate permanent orphan bytes. Storage and
capture metadata introduce additional availability and operational dependencies. Long publication
outages can stop folder progress when evidence can no longer cover the promised window. Blob
retention alone cannot recover an event that has expired from every retained source. Minimal
suppression metadata accumulates until teardown and remains user-linked despite omitting content.
