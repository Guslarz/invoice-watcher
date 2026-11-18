---
id: SPEC-PROCESSOR
title: Event processor operations
status: implemented
audience: [operator, developer]
modules:
  - libs/event-processor/api
  - libs/event-processor/impl
  - libs/event-processor/kafka-impl
  - libs/dao/event-processing/api
  - libs/dao/event-processing/firestore-impl
---

# Event processor operations

## Purpose

Registered processors share durable processing identities, failure handling and recovery so
operators can distinguish duplicates, transient failures, invalid records and review outcomes,
and replay retained evidence without changing its identity.

## Behavior

- **PROCESSOR-001** A processor declares its stage, processing version, supported input
  contracts and input, result, quarantine and replay topics. Only declared stages consume
  records; without a declared stage, processor runtime services, implementation settings,
  owned clients, executors and metric registries are not instantiated. Each stage uses an
  independent consumer group. A missing, blank, conflicting or unsupported required
  configuration for an active stage fails startup with the property or declaration name and
  no secret value. Importing an API alone creates no selector bean; missing or unsupported
  selection fails when an active capability requires that implementation, identifying the missing
  service interface. Ambiguous required implementations also fail startup.
- **PROCESSOR-002** A processor selects an input contract by the event type and schema version
  defined by EVENT-007, ignores unknown additive fields and validates required contract fields.
  Malformed bytes, unsupported contracts, legacy flat mail, missing tenant keys and missing or
  invalid source identities are quarantined explicitly without invoking the stage handler.
  The processor does not invent a source identity from message content or Kafka coordinates.
- **PROCESSOR-003** A processing identity is deterministic over the exact tenant key, stage,
  input event type, input schema version, source identity and declared processing version.
  Delivery coordinates, attempt number, replay request and timestamps do not participate.
  Redelivery and replay of a completed processing identity do not rerun its handler or create
  a new effective result. Changing tenant, stage or processing version creates a separate identity.
- **PROCESSOR-004** Every result uses the EVENT-007 envelope conventions and retains the tenant
  as its Kafka key. Its payload contains a stable result identity, processing identity, source
  references, processing method and version, original processing timestamps and a completed or
  review-required outcome. Source references include the input event type/version, source
  identity and original transport coordinates. Each emitted item has a deterministic local
  identity within the processing outcome. A review-required outcome is a durable result, not
  a technical failure. Result consumers apply repeated result identities idempotently.
- **PROCESSOR-005** Processing claims, admitted attempt counts, retry deadlines and outcomes
  survive restart. Only the current unexpired claim can save an outcome. Expired claims are
  recoverable and a stale worker cannot overwrite a newer outcome. Duplicate deliveries cannot
  concurrently establish different accepted outcomes for one processing identity.
- **PROCESSOR-006** A completed processing outcome and all its pending results are stored
  atomically. Pending results survive restart and are published until Kafka confirms them.
  A crash between publication and saving its confirmation may publish the same result again
  with the same identity and content. Completion is never saved separately from the pending
  results it requires. External service calls are outside this atomic guarantee and may repeat.
- **PROCESSOR-007** An input offset advances only after a completed outcome and its pending
  results are durable, or after Kafka confirms the record's quarantine copy and its failure
  disposition is durable. A pending quarantine copy is recovered before that input is skipped.
  Failure to store an outcome, publish quarantine or commit an offset does not silently discard
  the input. Offsets advance contiguously within each partition.
- **PROCESSOR-008** A record-local transient handler failure has a bounded retry budget.
  Attempt admission consumes the budget durably before invoking the handler; restart does not
  reset it. Retry delays use bounded jitter and durable deadlines. Exhaustion quarantines the
  input with a stable reason code. A non-retryable record failure is quarantined immediately.
  A timed-out handler loses permission to complete that claim.
- **PROCESSOR-009** Failure of shared processing infrastructure pauses affected work and
  exposes an operational failure instead of exhausting every input's handler retry budget.
  Unconfirmed outbox publication remains pending during broker unavailability. Recovery resumes
  pending work with its saved identity and retry budget; polling and heartbeat handling keep
  paused partitions assigned where possible. Paused work is visible to operators.
- **PROCESSOR-010** A quarantine record uses event type `processor.quarantined`, schema version
  `1`, and preserves the original key and value bytes losslessly alongside original topic,
  partition, offset and record timestamp, stage, processing version, admitted attempt count,
  failure time, stable failure identity and allowlisted reason code. Known tenant/source/
  processing identities are included; absent identities stay absent. Transport coordinates
  identify an invalid record's failure occurrence, not its business or source identity.
  Quarantine confirmation may repeat across a crash with the same failure identity. Credentials,
  arbitrary headers and raw exception messages are not added to quarantine diagnostics.
- **PROCESSOR-011** The shared replay service accepts explicit requests bounded by selected stage, partition,
  offset range, record limit, rate and processing version. Preview performs no writes and
  shows only sanitized selection metadata. Execution requires an operator identity, reason and
  request identity, saves a durable audit record, and delivers original key/value bytes through
  the selected stage's replay topic. The original source identity is preserved. A repeated
  request does not grant another retry budget. An authorized replay can reopen a quarantined
  processing identity with a fresh bounded budget, but cannot reopen a completed identity with
  the same processing version. A new version is explicit reprocessing. Unavailable evidence,
  mismatched stage/version and invalid replay authorization fail visibly without database surgery.
- **PROCESSOR-012** Per-stage measurements expose received and produced deliveries, completed,
  duplicate, retry, quarantined and review outcomes, handler duration, consumer offset lag,
  pending output count and age, paused state and last successful completion time. Metrics use
  bounded stage, topic, partition, outcome and allowlisted reason labels as applicable; tenant,
  source, processing, result, failure and replay identities are not metric labels. Delivery
  counters can repeat across failures and are not an accounting ledger. Unknown or unavailable
  lag and last-success measurements are not presented as healthy zero values.
- **PROCESSOR-013** Structured processor logs carry stage, processing version, outcome, attempt,
  safe correlation identifiers and allowlisted reason codes when available. Source and processing
  identities correlate related attempts; transport failure identity correlates invalid records.
  Logs, traces, replay previews and metric labels exclude raw tenant keys, e-mail addresses,
  message titles/bodies, attachments, extracted financial data, credentials, raw record bytes,
  unvalidated identifier fields and arbitrary exception text or nested exception causes.
- **PROCESSOR-014** Quarantine and pending result contents are sensitive data. Reading and
  replaying them requires authorized access and protected transport in deployments using real
  sensitive inputs. Quarantine has finite time-based retention; expiry ends its recovery window
  and replay reports expired evidence rather than claiming success. Confirmed outbox publication
  removes the stored result body while retaining its identity and disposition. Processing
  identity markers are retained for the supported replay horizon; this foundation does not
  expire them automatically or promise reconstruction from expired source evidence.
- **PROCESSOR-015** Input and output size limits are explicit. A result that exceeds the durable
  outcome limit is a non-retryable record failure before completion is saved. A quarantine copy
  that cannot fit its configured transport limit pauses the partition and raises an operational
  failure; it is neither truncated nor acknowledged as recovered. Topic retention and size
  settings are checked before a declared stage begins consuming.


## Acceptance

- **PROCESSOR-001** Given installed libraries without a stage, when starting the application,
  then no processing consumer starts; given independent declared stages, then groups differ;
  given incomplete or conflicting declarations/configuration, then startup fails with safe names.
- **PROCESSOR-002** Given supported mail with additive fields, when consumed, then known fields
  reach the handler; given malformed bytes, flat legacy mail, unsupported type/version, invalid
  source ID or absent/blank tenant key, then the handler is not called and quarantine states the
  reason without fabricating an identity. Include a null Kafka value and invalid key encoding.
- **PROCESSOR-003** Given the same occurrence at different offsets and repeated replay requests,
  when delivered before and after restart, then the completed identity has one effective outcome;
  changing tenant, stage or processing version yields independent processing. Identity test vectors
  cover Unicode, separator ambiguity and exact tenant casing without normalizing the mail key.
- **PROCESSOR-004** Given handlers returning multiple completed or review items, when publishing
  and redelivering, then result identities and accepted content/timestamps are stable, source
  references and method/version survive, the original tenant remains the key and reviews do not
  enter retry/quarantine. The fixture's result reader ignores additive fields and rejects unknown
  type/version pairs explicitly. No production recognition contract is asserted by the fixture.
- **PROCESSOR-005** Given concurrent claims and an expired worker, when a replacement completes
  and the stale worker returns, then only the replacement outcome is accepted; a restart retains
  admitted attempts and retry deadlines. A claim at its deadline cannot complete.
- **PROCESSOR-006** Given crashes before outcome commit, after atomic outcome/outbox commit,
  and after Kafka publication but before publication confirmation is saved, when restarting,
  then no committed result is lost and any repeated publication has the same result identity
  and bytes. The pending publisher recovers records without a fresh delivery of the input.
- **PROCESSOR-007** Given failing database writes, quarantine sends and offset commits, when
  recovering, then offsets never skip an unprotected record; confirmed duplicates and pending
  quarantine recover safely. Crash after quarantine acknowledgment but before saving its
  disposition can repeat that failure identity, and concurrent work cannot commit past a gap.
- **PROCESSOR-008** Given repeated transient handler failure, when retries occur across a
  restart, then at most the configured admitted attempts occur at bounded durable deadlines
  followed by quarantine; a permanent failure uses no extra attempt. A timed-out or crashed
  admitted attempt consumes budget and its late completion is fenced.
- **PROCESSOR-009** Given a database or broker outage, when processing is attempted, then
  affected work pauses visibly, pending output survives and the outage does not consume the
  remaining record-local retry budget; on recovery processing resumes without a quarantine flood.
- **PROCESSOR-010** Given arbitrary invalid value bytes, a null key/value, and header/exception
  secrets, when quarantining and reading the copy, then original key/value bytes and nullness
  round-trip, failure metadata is stable, no source ID is invented and no secret header or
  exception string is added. Include confirmation-loss redelivery and base64 size expansion.
- **PROCESSOR-011** Given retained quarantined records and a test caller of the shared service,
  when previewing a bounded selection,
  then nothing changes; when executing with explicit authorization, then audited requests
  preserve original bytes/source identity and only the selected stage receives replay. Repeat
  after crashes before and after sending: the request grants at most one new attempt budget.
  Replay of a completed identity skips its handler; an explicit supported new version creates
  new processing. Expired evidence, invalid authorization and unsupported versions fail visibly.
- **PROCESSOR-012** Given synthetic success, duplicate, retry, quarantine, review, backlog,
  paused and unavailable-broker cases, when inspecting the registry, then stage measurements
  have the documented semantics, missing values stay unknown and no identity becomes a label.
- **PROCESSOR-013** Given seeded secrets, raw content, malicious IDs, exception causes and
  replay requests, when capturing logs, traces and replay previews on success and failure paths,
  then only approved metadata appears; source/processing correlation still works for valid IDs.
- **PROCESSOR-014** Given a topic with unsuitable retention or inadequate operating access,
  when preparing a stage, then preflight reports the issue; given expiration of quarantine,
  then replay reports unavailable evidence. After acknowledged result publication its stored
  body is cleared, while replaying retained identity markers still suppresses duplicate work.
  The runbook distinguishes synthetic development tests from protected sensitive-data operation.
- **PROCESSOR-015** Given outcomes at and above the serialized size limit, when completing,
  then the accepted size persists and the excessive result is quarantined without partial
  output. Given an oversized quarantine envelope, then the partition pauses, its offset stays
  uncommitted and diagnostics expose only sizes and a safe reason. Exercise unavailable topic
  metadata and mismatched broker/topic/producer limits.


## Configuration

Properties, defaults and validation descriptions live in the generated reference:

- [Processing DAO selection](../../reference/configuration.md#invoice-watcher-event-processing-dao).
- [Firestore processing storage](../../reference/configuration.md#invoice-watcher-event-processing-dao-firestore).
- [Processor timing, capacity and replay bounds](../../reference/configuration.md#invoice-watcher-event-processor).
- [Kafka processor transport](../../reference/configuration.md#invoice-watcher-event-processor-kafka).

## Out of scope

Recognition, attachments, business matching, review resolution, legacy migration, arbitrary
historical backfills, exactly-once external effects, production deployment/security and an
executable replay interface. Future applications expose the reusable recovery service.
