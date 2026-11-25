---
id: SPEC-ATTACHMENT
title: Mail attachment evidence
status: implemented
audience: [user, operator, developer]
modules:
  - libs/mail-attachment/api
  - libs/mail-attachment/impl
  - libs/attachment-storage/api
  - libs/attachment-storage/gcs-impl
  - libs/attachment-storage/filesystem-impl
  - libs/dao/mail-attachment/api
  - libs/dao/mail-attachment/firestore-impl
  - libs/dao/mail-attachment/jpa-impl
---

# Mail attachment evidence

## Purpose

Retain bounded PDF attachment bytes privately for authorized processing and replay without
putting binary content into mail events. Evidence has a finite lifetime independent of processing.

## Behavior

- **ATTACHMENT-001** Newly scanned mail enumerates explicit attachment parts and named leaf parts
  in MIME order, including nested multipart containers. Attached messages and their descendants
  are excluded. Unnamed inline resources are excluded. A named inline part is an attachment candidate.
  Unsupported multipart attachments are reported without traversing their descendants.
  Bodies continue to follow MAIL-031 independently of attachment enumeration.
- **ATTACHMENT-002** Attachment evidence admits leaf parts declared as application/pdf. Admission
  preserves MIME-transfer-decoded bytes without PDF parsing, decompression, OCR or execution.
  The declared media type is untrusted and admission is not content validation or malware clearance.
  Other candidate types are reported as unsupported without storing their bytes.
- **ATTACHMENT-003** Attachment enumeration and capture enforce configured candidate-count,
  MIME nesting, per-attachment decoded-byte, aggregate decoded-byte, elapsed-time and serialized
  manifest-byte limits. Byte limits are enforced while reading, independently of declared sizes.
  An oversized attachment has a size-limit outcome without a stored reference. Reaching a global
  limit produces an explicit incomplete manifest and limit reason; it is never reported as a known
  empty or complete list. MIME decoding or infrastructure failures fail the scan under MAIL-041.
- **ATTACHMENT-004** Each candidate has a deterministic tenant-scoped attachment identity derived
  from the mail source identity and its one-based MIME part path. Repeated ingestion of the same
  occurrence reuses the same identity and immutable stored generation. Identical bytes in different
  occurrences or tenants are stored separately. A conflicting digest or size for an already captured
  identity is an integrity failure, not permission to overwrite its evidence.
- **ATTACHMENT-005** Each admitted attachment is uploaded to private blob storage before an event
  references its bytes. The reference identifies the configured store, opaque object key and exact
  immutable object generation. It includes the SHA-256 digest and decoded byte length. It contains
  neither attachment bytes, credentials nor a signed download URL.
- **ATTACHMENT-006** Capture reservations, immutable references, first-capture time, expiry and
  manifest policy version survive restart. An uncertain upload is reconciled by checking the
  deterministic object and its ownership, digest and size. Uploads are create-only. An uncertain
  event publication preserves the bytes and reuses the frozen manifest on retry; publication may
  repeat. No transaction across the database, blob storage and Kafka is promised.
- **ATTACHMENT-007** Evidence has a finite configured lifetime starting at its first durable capture
  reservation. Successful processing, failed processing, quarantine, redelivery and replay do not
  shorten or extend that lifetime. Uploads without a confirmed event expire by the same rule;
  publication uncertainty never authorizes early deletion.
- **ATTACHMENT-008** Configuration requires evidence lifetime to cover the maximum permitted
  capture-to-publication interval, supported replay horizon and recovery margin. Admission of each
  publication attempt requires sufficient remaining evidence lifetime for the supported topic
  replay horizon, recovery margin and bounded send duration. When that condition cannot be met, the folder fails visibly under
  MAIL-041 without advancing past an unconfirmed message. Existing duplicate suppression does not
  promise to recreate evidence after expiry. Topic retention and retained source records must also
  cover any advertised replay window; retaining bytes alone does not reconstruct an expired event.
- **ATTACHMENT-009** A tenant is the application user who owns the mailbox in user configuration,
  matching the owning user of the mail event. Retrieval requires trusted application-user context
  and matching capture ownership,
  source, attachment identity and exact stored generation. Only configured stores and owned object
  references are accepted; arbitrary URLs and cross-tenant references are rejected. Stored size and
  digest are verified. Expired, deleted, missing, unauthorized and integrity-failed evidence have
  distinct outcomes; none is presented as empty content. Temporary storage failures are retryable.
- **ATTACHMENT-010** At the evidence deadline, retrieval is denied even if physical deletion has
  not yet occurred. Storage cleanup independently deletes expired objects, including unpublished
  uploads, without relying on Kafka consumption or a retained event. Cleanup is asynchronous.
  Expired or deleted identities retain minimal suppression metadata for the installation lifetime
  so late retries cannot silently upload replacement evidence. Temporary capture files are removed
  on completion/failure and stale temporary files are recovered on restart.
- **ATTACHMENT-011** Raw attachment evidence is untrusted and accessible only to explicitly
  authorized internal capture or evidence-processing services and operators. No public download,
  browser preview or document parser is exposed by this capability. Filenames and raw tenant
  addresses are omitted from object names and attachment metadata. Logs, traces and metrics exclude
  attachment bytes, filenames, raw tenant addresses, signed URLs and arbitrary exception content.
  Operational counters distinguish captured, reused, unsupported, limited, expired and failed
  outcomes without tenant/source/attachment identifiers as metric labels.


- **ATTACHMENT-012** Active attachment storage is explicitly selected with
  invoice-watcher.attachment-storage.mode. Supported implementations are gcs and filesystem.
  Only the selected implementation binds its provider settings and creates its client or storage
  resources. Filesystem selection requires a directory and uses an independently configured logical
  store identifier; it does not initialize GCS clients or request Google credentials. A missing,
  unknown or ambiguous required backend fails startup. An event's generation is an opaque
  storage-specific token; its reference never exposes an absolute local path.
- **ATTACHMENT-013** Filesystem storage uses a dedicated private directory on a local POSIX
  filesystem supporting exclusive process locks and atomic create-only publication. An invalid,
  inaccessible, insecure or unsupported directory fails startup with a safe diagnostic naming the
  configuration property. A second process cannot concurrently own that storage directory.
  Object keys stay beneath the configured root; traversal, symlinked entries and unsafe generations
  are rejected. A completed entry atomically contains its bytes, owning-user scope, digest, size,
  original capture/expiry timestamps and immutable generation. Repeated matching writes return the
  existing generation; conflicting bytes or metadata never overwrite it. Interrupted writes leave
  no readable partial entry. Reopening the same directory preserves existing generations and
  references. Reads enforce size, generation, integrity and expiry checks.
- **ATTACHMENT-014** While filesystem storage is running, periodic cleanup removes expired
  application-owned entries independently of Kafka retention, publication confirmation or processing
  results. Startup performs expiry recovery and removes abandoned staging files before serving
  storage requests. No cleanup runs while the application is stopped; expiry is still enforced on
  reads after restart. Unrelated files and symlink targets are never deleted. Logical deletion
  suppression remains in the capture DAO, and physical cleanup follows the existing evidence
  deadline rather than successful processing.

- **ATTACHMENT-015** Capture persistence is explicitly selected with
  invoice-watcher.mail-attachment.dao.mode. Supported implementations are firestore and jpa.
  The JPA implementation persists capture reservations, ownership, fences, immutable manifests,
  first-capture/expiry timestamps and deletion/expiry suppression markers in its configured SQL
  database. The same atomic admission, stale-completion rejection, restart and duplicate guarantees
  apply to both implementations. Each selected backend owns its settings and clients; an inactive
  backend neither binds required provider settings nor discovers cloud credentials. The JPA graph
  can coexist with user-configuration persistence without borrowing that capability's clients.

## Acceptance

- **ATTACHMENT-001 / MAIL-033** Given nested mixed/related/alternative content, named inline PDFs,
  unnamed related images and attached messages, when scanning, then eligible candidates have stable
  MIME paths, excluded descendants are not traversed and body selection stays as MAIL-031 defines.
- **ATTACHMENT-002** Given PDF, non-PDF and encrypted/malformed PDF bytes, when capturing, then
  declared PDFs are retained as untrusted bytes without opening them and other types are explicit
  unsupported outcomes; no parser, antivirus or recognition claim is made.
- **ATTACHMENT-003** Given misleading MIME sizes, boundary byte counts, excessive nesting/count,
  slow reads and oversized manifests, when scanning, then bounds hold, truncation is explicit,
  no partial object is referenced and decoding/infrastructure failures follow MAIL-041.
- **ATTACHMENT-004** Given duplicate events, concurrent captures and identical bytes in different
  occurrences/tenants, when capturing, then one generation is reused per attachment identity,
  separate occurrences remain separate, and identity test vectors cover Unicode and MIME paths.
  A same-identity byte conflict fails without overwriting the accepted generation.
- **ATTACHMENT-005 / 006** Given crashes before upload, after upload with lost response, after
  manifest persistence, after Kafka acknowledgment and before cursor persistence, when recovering,
  then no event references an incomplete upload and duplicates reuse the immutable generation.
  Broker ambiguity retains the object and retries the same frozen manifest.
- **ATTACHMENT-007 / 008** Given completed, failed, quarantined and unpublished captures, when time
  advances across retry/replay and expiry, then the first reservation/deadline stays unchanged.
  Invalid retention combinations fail startup; a publication with insufficient remaining evidence
  lifetime fails visibly
  without cursor advancement, byte resurrection or silent skipping.
- **ATTACHMENT-009** Given valid access, cross-tenant or arbitrary store/key references, missing,
  expired or corrupted objects and temporary outages, when retrieving, then valid bytes are checked
  and distinct safe failures enforce authorization and integrity before exposing content.
- **ATTACHMENT-010 / DEPLOY-044** Given uploaded objects whose Kafka events were never confirmed
  or have expired, when evidence lifetime passes, then reads are denied and configured lifecycle
  deletion still applies; late retries cannot recreate evidence. Assert lifecycle configuration
  and logical expiry locally; exercise actual deletion asynchronously in a dedicated cloud check.
- **ATTACHMENT-011** Given seeded sensitive strings in filenames, bytes and exceptions, when
  capturing/retrieving/failing, then diagnostics stay allowlisted, metrics have bounded dimensions,
  temporary files are cleaned and no public retrieval or parsing endpoint exists.
- **EVENT-004 / 009** Given old envelopes and new stored/unsupported/limited/empty manifests, when
  serializing/reading, then manifest nullness, completeness and evidence references round-trip,
  unknown fields remain compatible, and old readers can consume the unchanged known fields.
- **DEPLOY-043 / 044** Given rendered deployment configuration, when checking it, then the bucket
  is private, uses the configured region, denies public access and disables versioning/soft delete;
  identity bindings separate mutation/deletion permissions, and down includes blob deletion.

- **EVENT-014 / PROCESSOR-016** Given ingestion, result, quarantine and replay topic configuration,
  when provisioning or preparing local examples, then each application topic has an explicit
  seven-day retention unless a documented stage override exists. Given missing or mismatched
  effective retention, cleanup or byte limits, when producer/stage preflight runs, then publishing
  or consumption fails visibly. Include pre-existing topics: create-if-absent alone must not hide
  drift. Application topic policy does not alter Kafka internal compacted topics.
- **ATTACHMENT-012** Given GCS and filesystem implementations on the classpath, when explicitly
  selecting filesystem with a private local directory, then only filesystem resources initialize;
  cloud clients and credential discovery remain inactive.
- **ATTACHMENT-013** Given concurrent matching writes and a backend restart, when reading the
  resulting reference, then one immutable generation and exact bytes remain; conflicts, interrupted
  publication, traversal, symlinks and wrong generations cannot expose or overwrite evidence.
- **ATTACHMENT-014** Given expired objects and abandoned staging files alongside unrelated files,
  when startup or periodic cleanup runs, then only expired owned objects and staging files are
  removed; stopped cleanup never extends read availability.
- **ATTACHMENT-015** Given persistent H2 capture state, when competing for first admission,
  recovering a lease, restarting, deleting or expiring evidence, then one reservation/fence governs
  completion and suppression markers prohibit recreation; wrong owners cannot mutate records.
  Given other JPA capabilities in the same context, each owns its independent persistence graph.

## Configuration

Configuration is listed in the generated [reference](../../reference/configuration.md).

## Out of scope

Malware clearance, document parsing, recognition, public downloads, archives, attached messages,
cross-message deduplication, audit archives and evidence recovery after complete environment teardown.
