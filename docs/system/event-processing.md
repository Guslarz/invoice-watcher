# Event processing foundations

InvoiceWatcher provides reusable processing and recovery services for applications that consume
mail events. Invoice recognition, payment recognition and matching applications are not yet
implemented or deployed. Installing the libraries registers no consumer until an application
declares a stage, its handler, processing version, supported contracts and topics. Disabled
stages create no owned clock, executor, registry, transport or implementation settings. Selector
metadata does not instantiate an API properties bean; active features validate implementation
availability independently of the values suggested by metadata.

Each stage gets its own consumer group. Inputs use the existing typed event envelope and exact
owning-user Kafka key. New result types use the same envelope conventions, with their own schema
versions. Extra additive fields are tolerated; unknown event types/versions are explicit failures.
Historical flat mail has no reliable source identity and is quarantined instead of guessed into
an identity. Attachments are outside this foundation.

## Identities and outcomes

Processing identities include tenant, stage, input type/schema version, source occurrence identity
and processing version. Kafka offsets and replay request IDs do not replace the source identity.
The same source/version can be delivered repeatedly without redoing completed work. A deliberate
new processing version creates independent processing; it does not silently overwrite prior facts.

Handlers produce result candidates with stable local item identities. The accepted result includes
its stable result/processing identities, source reference and original transport coordinates,
method/version, timestamps, outcome, reason codes and capability-owned evidence. Accepted bytes and
timestamps stay fixed during republication. Recognition-specific fields and confidence rules belong
to the future recognition applications. An input may complete with no relevant results.

A review-required result is a successful processing outcome carrying evidence for later review.
It is not a failed record and does not enter the technical retry/quarantine loop. The foundation
provides no manual-review resolution workflow.

## Durable recovery

Processing state is accessed through a DAO API. Its initial implementation uses Firestore in
Datastore mode, isolated from user-configuration entities. Handler code receives no database client.
Persisted claims fence stale workers, and retry admission/deadlines survive process restarts.

Accepted outcomes and pending result values are stored atomically. The input can then be committed.
An independent outbox pass sends pending results until Kafka confirms them and removes their stored
bodies after saving confirmation. A crash after publication can repeat the same result identity and
bytes, so downstream applications must apply results idempotently. External calls can also repeat;
this mechanism does not make a parser invocation or database/Kafka pair one distributed transaction.

Transient record failures use a bounded attempt budget; permanent failures go directly to quarantine.
A shared database/broker outage pauses affected work instead of quarantining an entire backlog.
Retry waiting uses Kafka consumer pause/poll handling; it does not sleep a handler thread through a
long backoff. The affected consumer thread's assigned partitions wait together; concurrency controls
how many threads a stage uses. No business ordering across original and replay topics is promised.

Before a failed input is acknowledged, a pending failure disposition is stored, Kafka confirms its
lossless quarantine copy, and that confirmation becomes durable. A crash can repeat the same failure
identity. The quarantine record retains original key/value bytes and coordinates, including null or
malformed values, plus sanitized failure metadata. It excludes arbitrary original headers and raw
exception messages. Offsets alone do not pin Kafka retention: stalled work can outlive its evidence.

## Replay and data handling

Replay is an explicit, bounded service operation with a write-free preview. Execution requires an
operator/reason/request identity, stage, supported processing version, partition/range, limit and
rate. The adopting application must authenticate callers; a supplied operator name is audit metadata,
not authentication. There is no standalone replay executable or deployed endpoint in this change.

The service durably audits requests and publishes original bytes to the selected stage's replay
topic. Consumers validate replay authorization against the saved request and retained quarantine
evidence. Repeating a request cannot repeatedly grant fresh attempts. Authorized replay can reopen a
quarantined identity once with another bounded budget; completed identities remain complete at the
same version. Changing the processing version is explicit reprocessing. Replay does not repair a
malformed record or fabricate a source identity for legacy mail.

Quarantine retention is finite and determines its recovery window. Missing/expired evidence fails
visibly. Completed processing markers have no automatic expiry; they preserve duplicate detection
while source history remains replayable. Pending result bodies and quarantine values are sensitive
data even though ordinary logs contain only safe metadata. See [preparation and recovery](../setup/event-processing.md)
for the test workflow, settings, capacity/access requirements and alert guidance.

## Attachment evidence windows

Mail events now carry a separate attachment manifest. An absent manifest in older records means
attachments were not captured; a complete empty manifest means no candidates were found. Stored
PDF references identify untrusted evidence, not validated or malware-cleared documents. Consumers
that only need the body can ignore this additive field.

The ingestion topic has seven-day retention; attachment bytes have a nine-day first-capture
lifetime including publication and recovery margins. No quarantine extension is included. A future
recognition stage must account for every input/quarantine/replay window before retaining references
longer. Expired evidence fails visibly instead of producing an empty document. See
[attachment operation](../setup/mail-attachments.md) for retry and access semantics.
