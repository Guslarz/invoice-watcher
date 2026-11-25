# Prepare event processors and recovery

The shared processor runtime is a library foundation. No recognition consumer, standalone helper
application or replay endpoint is shipped. The integration test suite composes a synthetic stage
with Kafka and both Datastore and H2 persistence and exercises recovery through the public service API.

## Exercise the foundation locally

1. Install Java 25 and make Docker available as described in [local development](local.md).
2. From the checkout root, run:

    ```shell
    ./gradlew :libs:event-processor-impl:test
    ```

    Testcontainers starts the pinned Kafka broker and Datastore emulator, creates isolated test
    topics/namespaces and removes the containers afterwards. It uses synthetic mail/evidence and
    needs no Google credentials or existing local broker. Tests cover retry/restart, claim fencing,
    uncertain publication, real listener offset commits, quarantine and replay. No production data
    should be copied into fixtures.

3. Run the complete repository checks before proposing a change:

    ```shell
    scripts/verify.sh
    ```

This is the currently runnable processing/recovery workflow. Future application plans must expose
an authenticated operational entry point before claiming operators can execute production replay.

## Configure an adopting application

Applications compose the API-backed runtime, Kafka adapter and an explicitly selected Firestore or JPA implementation. See the
[generated module reference](../reference/modules.md) for build dependencies. They register a stage
with an explicit name/version, allowed input/result contracts, handler and distinct source/result/
quarantine/replay topics. Stages use separate consumer groups; result and replay keys retain the
exact input tenant. A handler's declared reason-code allowlist bounds review metrics.

Processing configuration is under `invoice-watcher.event-processor`, with Kafka and replay bounds
under its `.kafka` and `.replay` groups. Storage selection is
`invoice-watcher.event-processing.dao.mode`, selecting `firestore` or `jpa`. Missing or unsupported mode values fail
startup through required storage dependency wiring when an active stage requires storage; there
is no implicit local/in-memory store.
The selector is a string. Each implementation contributes its own expected value through metadata;
adding a backend does not change the API. API metadata alone registers no beans. A stage declares
its required implementation through dependency injection. Missing or ambiguous backends fail
startup; selected backends validate their own settings. All property defaults and constraints live
in the [generated configuration reference](../reference/configuration.md).

Set the storage `.firestore.project-id` and, where applicable, `.database-id` and `.namespace`.
Omit `.host` to use Google credentials discovery. Supply the deployment's application identity
through the existing credentials mechanism; do not put credentials in source or command arguments.
A local emulator uses an explicit `.host=http://localhost:<emulator-port>` with synthetic data and
no Google credentials. Processing state has its own namespace and does not reuse mailbox entities.

For local SQL persistence, select JPA independently of user and capture storage:

```yaml
invoice-watcher:
  event-processing:
    dao:
      mode: jpa
      jpa:
        data-source:
          basic:
            url: jdbc:h2:file:${user.home}/.local/share/invoice-watcher/processing
            username: sa
            driver: org.h2.Driver
        ddl-auto: update
        dialect: org.hibernate.dialect.H2Dialect
```

Prepare that private parent directory as described in [local attachment setup](mail-attachments.md#local-operation).
The capability owns its datasource, entities and transactions; JPA selection does not initialize
Firestore clients or cloud credentials. A file-backed H2 database preserves claims, accepted
outcomes, pending publications and replay progress across restarts. Outcome and pending outputs
commit together. Uncertain publication retains the output; confirmation clears sensitive stored
output bytes while keeping its identity. Do not clear the database to retry work: retained
suppression and replay state enforce duplicate budgets. `update` is a local development schema
policy, not a production migration strategy. Use an encrypted Secret for any JDBC password.
Adding this library does not create a stage or start a consumer.

Set `.kafka.bootstrap-servers` and explicitly choose `.kafka.offset-reset` for a new group:
`earliest`, `latest` or `none`. That choice controls whether retained input is processed, skipped
or requires a separately prepared offset. Existing group offsets remain authoritative. No runtime
operation silently resets a live consumer group.

Standard Kafka client security settings are read from `spring.kafka.properties`, including
`security.protocol`, SASL and SSL settings. Configure secrets through the deployment's secret
injection. Before using real sensitive data, authenticate clients, authorize only the intended
stage/operator identities and protect transport. A separate topic name alone provides no access
control. Startup topic checks inspect configuration; they do not certify an entire deployment's
security or create ACLs.

## Prepare and verify topics

The runtime does not create topics or change retention automatically. The following commands are
preparation examples for a future stage called `invoice-recognition`, using the existing local
Kafka container. They do not start such a processor. Start the local broker using the procedure
in [local development](local.md), and inspect its `all-mails` topic before choosing partitions.
The example uses the local broker's single replica; a production design must select its own
replication, backups and availability settings.

```shell
docker compose -f .run/files/kafka.yml exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic invoice-recognition.results --partitions 1 --replication-factor 1 \
  --config cleanup.policy=delete --config retention.ms=604800000 \
  --config retention.bytes=-1 --config max.message.bytes=1048588

docker compose -f .run/files/kafka.yml exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic invoice-recognition.quarantine --partitions 1 --replication-factor 1 \
  --config cleanup.policy=delete --config retention.ms=604800000 \
  --config retention.bytes=-1 --config max.message.bytes=4194304

docker compose -f .run/files/kafka.yml exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic invoice-recognition.replay --partitions 1 --replication-factor 1 \
  --config cleanup.policy=delete --config retention.ms=604800000 \
  --config retention.bytes=-1 --config max.message.bytes=2097152
```

`--if-not-exists` does not update existing settings. Read back the source and new topic descriptions
and each topic's effective configuration; repeat these commands for the relevant topic names:

```shell
docker compose -f .run/files/kafka.yml exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic all-mails

docker compose -f .run/files/kafka.yml exec kafka /opt/kafka/bin/kafka-configs.sh \
  --bootstrap-server localhost:9092 --entity-type topics \
  --entity-name invoice-recognition.quarantine --describe --all
```

Preflight requires explicit finite quarantine time retention, delete-only cleanup and no competing
byte-retention cap that silently shortens the configured window. The quarantine topic must accept
at least the configured publication capacity. Source record size must fit quarantine after base64
expansion, duplicated key and diagnostic overhead; replay must accept the original plus its replay
headers. Result capacity must cover the runtime's maximum serialized outcome and overhead. Existing
incompatible topic settings fail startup with a safe error and must be corrected by an authorized
administrator before a stage starts.

Retention is not an exact deletion deadline for individual records: Kafka removes eligible log
segments. Monitor disk usage as well as elapsed time. The development broker's data disk is small;
measure average encoded record size and expected failure volume before relying on a recovery window.
Alert before disk pressure can prevent quarantine writes. When a record cannot fit quarantine,
its partition remains uncommitted and diagnostics show safe sizes; raise compatible topic/client
limits with capacity review, then resume. Do not truncate evidence to clear the backlog.

## Handle failures and replay

The runtime persists retry admission before calling a handler. Retries follow bounded jittered
backoff; restarts retain the budget. Handler timeout cancels work and fences its eventual result.
A shared infrastructure failure waits for recovery without consuming the remaining record-local
budget. Confirmed outcomes and pending results survive restart. A pending result is republished
without calling its handler, and its database payload is cleared after confirmation is recorded.

The initial settings and their validation are listed in the configuration reference. Handlers must
honor execution deadlines and document external-service idempotency and costs. Library code cannot
undo a remote extraction call after a timeout.

The adopting application's operational interface must invoke `ReplayService.preview(request)`
and `ReplayService.execute(request)` after authorization. A request selects one declared stage and
processing version, quarantine partition, inclusive start/exclusive end offsets, record limit and
rate. Operator/reason/request identifiers are bounded operational identifiers. Use no private text
in them. An example service request for a single retained quarantine record is:

```java
var request = new ReplayRequest(
    "recovery-20261116-01", "invoice-recognition", "v1",
    0, 42, 43, 1, 1.0, "on-call", "DEPENDENCY_FIXED");
var preview = replayService.preview(request); // sanitized selection; no writes
// The application's authorized operator workflow reviews the selection.
replayService.execute(request);
```

These calls illustrate library integration, not an executable command shipped by the repository.
The synthetic integration tests are the current executable example. A future application must
supply the authenticated interface and bind the declared stage/version before these names work.

Before execution, fix the underlying failure, confirm evidence still exists, preview the exact
range and assess external-call cost, output volume and downstream side effects. Replaying a
completed identity at the same processing version skips its handler. A corrected algorithm uses
an explicitly deployed new processing version with its own downstream migration/reprocessing plan.
Replay arrives through a separate topic and can interleave with live input.

A request is audited before sends. After an interrupted execution, retry the same request identity
with the same selection to resume its saved cursor. Conflicting selection reuse is rejected.
A later failure in a range does not undo records already sent; the audit retains its partial
progress and failure even if an older send confirms afterwards. Publication may repeat when
confirmation is uncertain; the consumer admits at most one new budget
per authorized processing identity/request. New requests cannot reset active work. There is no
automatic loop from quarantine into replay.

If preview reports expired evidence, do not reset offsets or edit marker entities. Determine
whether retained input or another separately authorized source can support a future reprocessing
procedure; this service does not reconstruct deleted bytes. Missing source evidence during a
stalled quarantine handoff also requires operator investigation, since uncommitted offsets do not
prevent Kafka retention. Processing markers intentionally have no automatic TTL. Shortening their
retention would require an explicit replay-horizon and tenant-data lifecycle design.

## Monitor a stage

Meter names use `invoice.processor` with stage labels. Delivery counters distinguish received,
produced results, completed, duplicate, retry, quarantine and review outcomes. They can repeat across
failures and are not a financial ledger. Handler duration measures admitted attempts. Durable state
provides pending-output count/oldest age and last successful completion time; last success is unknown
until a completion exists. Consumer lag is the end offset minus the committed group offset for each
stage/topic/partition; uninitialized or unavailable offsets are unknown rather than healthy zero.
The paused gauge includes retry waiting and infrastructure pauses. Review reasons come from each
handler's allowlist. Tenant and source identities never become metric labels.

An adopting application must expose its registry through its chosen monitoring setup; no new
monitoring deployment or alerts are installed here. Initial alert guidance is:

| Signal | Suggested starting condition | Operator action |
|---|---|---|
| Work paused or input lag rising | Persists for five minutes while new input arrives | Check broker/storage availability, claims and timeout rates |
| Pending results aging | Oldest exceeds five minutes | Check broker capacity and publication failures; preserve pending state |
| Quarantine increasing | Sustained increase over five minutes | Inspect safe reason codes and preview a bounded recovery selection |
| Repeated review reason | Sustained increase against the stage's expected baseline | Investigate extraction/evidence quality with authorized access |
| Disk pressure or evidence nearing expiry | Capacity forecast cannot cover the recovery window | Reduce incident duration or provision reviewed capacity before data expires |

Tune thresholds to stage traffic and expected processing time before enabling alerts. A quiet stage
can legitimately have an old last-success timestamp; combine it with incoming work and lag.
Logs correlate valid source/processing IDs and failure occurrences but omit tenant keys, addresses,
mail content, financial fields and arbitrary exception causes. Access quarantine/result bodies only
through authorized tooling; do not paste them into ordinary logs or incident messages.

## Explicit application topic lifetimes

The preparation examples explicitly assign seven-day retention to each result, quarantine and
replay topic, with delete-only cleanup and no byte-retention cap. The deployed ingestion topic also
uses seven days, as configured by the checked-in installation policy. Processor configuration
records the expected retention for each topic role, and startup compares those expectations to the
effective broker settings. Internal Kafka offset and transaction topics have their own compacted
broker-managed semantics; this policy concerns application topics.

The example stages are not deployed by these commands. Their separate retention windows do not
extend attachment deadlines. Current attachment sizing assumes no quarantine; before deploying a
stage that retains attachment references, approve evidence retention covering its complete recovery
path. See [attachment evidence](mail-attachments.md).
