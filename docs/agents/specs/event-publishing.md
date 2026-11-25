---
id: SPEC-EVENT
title: Event publishing
status: implemented
audience: [operator, developer]
modules:
  - libs/event-producer/api
  - libs/event-producer/kafka-impl
  - libs/mail-producer/task
---

# Event publishing

## Purpose

Every message picked up by [mail scanning](mail-scanning.md) is published as an event, so that
other services can decide whether it is an invoice and process it. InvoiceWatcher does not know
or care who consumes the events.

## Behavior

- **EVENT-001** Each picked-up message is published as exactly one event, in the order the
  messages are stored in the folder (oldest first).
- **EVENT-002** All events go to one topic, configured by
  `invoice-watcher.mail-producer.task.topic` (`all-mails` in the dev environment). The topic is
  required.
- **EVENT-003** The event key is the name of the user who owns the mailbox (their e-mail
  address), so all events of one user land in the same partition, in order.
- **EVENT-004** The event value is a JSON envelope with `eventType`, `schemaVersion` and
  `payload`. For mail ingestion, `eventType` is `mail.ingested`, `schemaVersion` is integer
  `1`, and `payload` is a JSON object with the following fields:

    | Field | Type | Content |
    |-------|------|---------|
    | `sourceId` | string | Stable tenant-scoped source identity defined by EVENT-005 |
    | `sender` | string | The `From` address, e.g. `Shop <billing@shop.example>`; `null` when the message has no `From` header |
    | `title` | string | Subject |
    | `date` | date-time | When the message was sent, in UTC; when the message has no valid `Date` header, when the server received it |
    | `content` | string | Decoded selected body, or `null` when no supported body exists |
    | `contentType` | string | `text/html`, `text/plain`, or `null` when no supported body exists |

The mail payload also contains `attachmentManifest`. Its `version` is
  integer 1, `complete` states whether all eligible candidates were enumerated, `limitReason` is
  null or an allowlisted global-limit reason, and `attachments` is an ordered array. Each entry has
  `attachmentId`, `partPath`, `declaredContentType`, `outcome`, and nullable `evidence`. Outcomes are
  `stored`, `unsupported-type`, or `size-limit`. Evidence contains `store`, `objectKey`, `generation`,
  `sha256`, `byteLength`, `capturedAt` and `availableUntil`; it exists only for `stored` entries.
  Stored means retained untrusted bytes, not malware-cleared or recognition-ready content.

- **EVENT-005** Each newly published mail event has a deterministic source identity scoped by
  the owning user's name, IMAP server host and port, authenticated account name, folder name,
  UIDVALIDITY and message UID. Repeated delivery and re-ingestion with the same identity inputs
  produce the same `sourceId`, including after configuration recreation or credential renewal.
  Passwords, OAuth tokens, grant identifiers, configuration identifiers, scan positions and
  message content do not participate in the identity. The server host is lowercased using
  locale-independent rules; the special folder name `INBOX` is case-insensitive and represented
  as `INBOX`; other folder names, user names and account names are preserved exactly.
  `sourceId` is `imap-v1:` followed by the lowercase hexadecimal SHA-256 digest of the canonical
  encoding described below. The raw account name is not added to the event payload.

  The canonical encoding is the UTF-8 bytes of `imap-v1` followed, in order, by the seven
  normalized identity inputs above. Each component, including `imap-v1`, is encoded as its
  UTF-8 byte length in unsigned decimal ASCII, a colon, and its UTF-8 bytes. Port,
  UIDVALIDITY and UID use decimal ASCII without leading zeros. Component boundaries are
  independent of punctuation and Unicode in names.

- **EVENT-006** Source identity identifies an IMAP folder occurrence, not a logical document.
  Different tenant, server, port, account, folder, UIDVALIDITY or UID inputs identify separate
  occurrences. Copying or moving a message to another folder, changing a mailbox endpoint or
  account, or resetting UIDVALIDITY may produce a new identity for the same logical mail.
  Sender headers, `Message-ID`, dates and content are not substitutes for source identity.

- **EVENT-007** Events identify their contract by the pair (`eventType`, `schemaVersion`).
  An envelope requires a nonblank event type, a positive integer version and a non-null payload.
  Version `1` of `mail.ingested` describes the entire envelope and its mail payload.
  Consumers dispatch by this pair, ignore unknown additive fields they do not need, and
  reject unsupported types or versions explicitly rather than guessing a payload type.
  Additive changes preserve existing meanings; breaking changes require a new version and
  an explicit migration/replay plan. Historical flat events without the envelope are legacy
  unversioned mail events and have no guaranteed source identity; consumers must not fabricate
  one from content or Kafka offsets. New records use the envelope on the existing configured
  topic without Java class-name type headers; retained flat records are not rewritten or
  automatically backfilled, and there is no dual publishing. Existing consumers must be
  updated for this breaking change before producer rollout. The Kafka key remains the
  owning user's name.

- **EVENT-008** If any required identity component is absent or invalid, no event is published
  for that message. User, host, account and folder names must be nonblank, the port must be
  between 1 and 65535, and UIDVALIDITY and UID must be between 1 and 4294967295. The folder
  fails under MAIL-041 and its scan position advances only through events already confirmed
  under EVENT-012. Failure diagnostics do not expose credentials or the canonical identity
  encoding.

- **EVENT-010** For an active publishing capability, the event backend is selected with
  `invoice-watcher.event-producer.mode`. The only supported value is `kafka`; with it, events are sent to the brokers in
  `invoice-watcher.event-producer.kafka.bootstrap-servers`, keys as strings, values as JSON.
  `invoice-watcher.event-producer.kafka.bootstrap-servers` is required in `kafka` mode. A missing
  or unknown backend selection fails startup identifying the required event producer interface.
  Ambiguous event producer implementations also fail startup.
- **EVENT-011** A message is considered handed over once the event backend confirms it has stored
  the event. The folder's scan position is saved once, after all messages of the folder are handed
  over.
- **EVENT-012** If the event backend does not confirm an event, handing over fails, the folder's
  position is saved at the last confirmed event and the folder is treated as failed
  ([MAIL-041](../specs/mail-scanning.md)). An event can still be published twice, e.g. when the
  application stops before the position is saved: delivery is at-least-once, consumers must
  tolerate duplicates.
- **EVENT-013** A missing required event property fails the startup and names the property.

- **EVENT-009** Mail attachment manifests are additive fields of mail.ingested schemaVersion 1.
  Readers that do not use attachments ignore them. An absent manifest in retained events means
  attachments were not captured; a present complete empty manifest means no candidates were found.
  Retries of a captured message reuse its frozen attachment manifest, identities and references.
  Kafka confirmation remains the handover boundary and failures retain EVENT-012 cursor semantics.

- **EVENT-014** Each application Kafka topic has an explicitly configured finite time-based
  retention period and documented cleanup policy and byte-retention limit. The mail ingestion topic
  uses delete-only cleanup, retention.ms 604800000 (seven days), retention.bytes -1 and broker
  append timestamps. The producer checks these effective settings before publishing; unsuitable
  or unavailable settings prevent publication with a visible operational failure. Broker defaults
  alone are not the retention declaration. Increasing ingestion retention requires an evidence
  lifetime compatibility check before adoption. Kafka retention is asynchronous segment cleanup,
  not a promise to keep events indefinitely or to delete each event at an exact instant.

## Acceptance

- **EVENT-001** Given a folder with three new messages, when it is scanned, then three events
  appear on the topic, in the order the messages were received.
- **EVENT-003** Given user `jan@example.com`, when one of their messages is published, then the
  event key is `jan@example.com`.
- **EVENT-004** Given supported, absent and empty bodies, when publishing with valid identity
  inputs, then serialized JSON contains `eventType: "mail.ingested"`, integer `schemaVersion: 1`,
  and a `payload` containing the five existing message fields and a nonblank `sourceId`.
- **EVENT-005** Given identical tenant/mailbox/folder/UIDVALIDITY/UID inputs, when retrying after
  a publish-before-position-save failure, resetting scan progress, recreating configuration or
  renewing credentials, then all emitted copies have the same source identity.
- **EVENT-005** Given equivalent host casing or casing of `INBOX`, when publishing, then
  identities agree; given punctuation, non-ASCII names and concatenation-ambiguous tuples,
  then canonical encoding has fixed test vectors and distinct component boundaries.
- **EVENT-005** Given both basic and Gmail OAuth credentials for the same account and endpoint,
  when the other inputs agree, then source identity agrees and neither secrets nor the raw
  account name are newly exposed by the identity fields or failure diagnostics.
- **EVENT-006** Given a baseline input, when independently changing each of tenant, host,
  port, account, ordinary folder name, UIDVALIDITY or UID, then the source identity changes.
  Given identical content in two folder occurrences, then it does not merge their identities.
- **EVENT-007** Given a supported envelope with unknown additive fields at either level,
  when read by a consumer fixture, then known fields are readable; given an unsupported
  type/version, then the fixture explicitly reports it as unsupported. Given a legacy flat
  event, then it is distinguishable as unversioned and has no guaranteed source identity.
  Given the existing topic with historical flat records, when the new producer publishes,
  then new records carry envelopes without Java class-name headers, historical records retain
  their format, and the tenant key remains unchanged.
- **EVENT-008** Given missing, blank or out-of-range identity components, when scanning, then
  the invalid message is not published, the folder fails, and the persisted position stops
  at the last confirmed event. Cover both invalid-first and invalid-after-confirmed cases,
  UIDVALIDITY reset, and credential-safe failure diagnostics.

- **EVENT-010** Given no `invoice-watcher.event-producer.mode`, when the application starts, then
  it fails with an error naming `invoice-watcher.event-producer.mode`. Given
  `invoice-watcher.event-producer.mode=rabbit`, when the application starts, then it fails with an
  error naming that property.
- **EVENT-011** Given the backend confirms every event, when a folder with two new messages is
  scanned, then the position is saved once, at the second message.
- **EVENT-012** Given the backend rejects the second of three events, when the folder is scanned,
  then an error is logged, the position is saved at the first message, the remaining folders of
  the mailbox are skipped, and the next scan publishes the second and third messages.
- **EVENT-013** Given `invoice-watcher.event-producer.mode=kafka` and no `bootstrap-servers`, when
  the application starts, then it fails with an error naming
  `invoice-watcher.event-producer.kafka.bootstrap-servers`. Given no
  `invoice-watcher.mail-producer.task.topic`, when the application starts, then it fails with an
  error naming that property.

- **EVENT-009** Given old envelopes and new complete, incomplete and empty manifests, when
  consuming schemaVersion 1, then old readers retain known fields and attachment-aware readers
  distinguish not-captured history from an empty candidate list. Duplicate capture reuses references.
- **EVENT-014** Given a topic with explicit seven-day delete-only retention, no byte-retention
  cap and append timestamps, when publishing, then preflight succeeds; changing effective policy
  prevents publication with a safe operational failure.

## Configuration

Properties, their types, defaults and meaning are listed in the generated reference:

- [`invoice-watcher.mail-producer.task`](../../reference/configuration.md#invoice-watcher-mail-producer-task)
- [`invoice-watcher.event-producer`](../../reference/configuration.md#invoice-watcher-event-producer)
- [`invoice-watcher.event-producer.kafka`](../../reference/configuration.md#invoice-watcher-event-producer-kafka)

## Out of scope

- Consuming events and recognizing invoices.
- Exactly-once delivery: consumers must tolerate duplicates.
