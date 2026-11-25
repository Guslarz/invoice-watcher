# Mail attachment evidence

The producer retains PDF attachment bytes in private storage and places
metadata and an immutable object reference in the mail event. A stored PDF is untrusted evidence:
this delivery does not open it, extract invoice facts, scan it for malware or expose downloads.
Deployed dev uses Google Cloud Storage; local operation can explicitly select a private directory.

## Capture and limits

Explicit attachments and named MIME leaf parts are candidates, including named inline PDFs.
Unnamed inline resources and attached messages are excluded. Non-PDF candidates are reported as
unsupported; unsupported multipart attachments are not traversed for nested files. Admission uses the declared application/pdf media type; that declaration is untrusted.
The original filename is not needed for identity and is omitted. The stored bytes are decoded from
MIME transfer encoding without conversion or document parsing.

Attachment capture is bounded by decoded per-file and per-message bytes, candidate count, MIME depth,
manifest size and elapsed time. See [attachment configuration](../reference/configuration.md#invoice-watcher-mail-attachment)
for properties and defaults. Excessive files have a size-limit outcome. A global limit marks the
manifest incomplete with a reason. Neither case is reported as a known empty attachment list.
Transport or MIME decoding failures fail the folder scan rather than publish incomplete evidence
as if it had been captured successfully. These capture limits do not bound the earlier selected-body
decoding or the total duration of a folder scan.

The producer reads each message while its IMAP folder is open, uploads accepted bytes and durably
saves its capture manifest before sending the event. The folder cursor advances only through
Kafka-confirmed messages. An upload failure or unconfirmed event leaves that message eligible for
retry. The mailbox occurrence must remain available until handover; capture metadata is not an
archive of the complete original mail event.

## Duplicate delivery and access

A stable identity combines the owning application user, mail occurrence and MIME part path.
Repeated deliveries reuse the same object generation and deadline, including after restart.
Identical documents in different messages or users remain separate stored evidence. An upload
whose response was lost is reconciled against the existing object; conflicting bytes fail integrity
checks instead of overwriting it. Database leases fence late capture attempts.

The owning user is the application user in mailbox configuration, not a Google Cloud principal.
Internal callers supply trusted application-user context and the source/attachment identity.
Retrieval checks persisted ownership and the exact recorded reference, validates the configured
store and object generation, then verifies byte count and SHA-256. Possessing a reference does not
grant access. The bucket is private, accessed by workload identity, and has no public preview or
user download API. The producer has its own cloud identity; registration's cloud identity does not
receive attachment bucket permissions.

## Retention and failure recovery

The checked-in [installation policy](environment.md#attachment-storage) explicitly selects seven
days of all-mails topic retention and nine days of evidence availability from the first durable
capture reservation. This allows up to approximately one day before Kafka publication plus one
day of processing/recovery after the seven-day event window. There is no additional quarantine
window in this delivery. A future stage that keeps attachment references must review its complete
input, quarantine and replay path before promising longer recovery.

Before each publication, the producer requires enough remaining evidence lifetime for the entire
input retention window, the recovery margin and the bounded send operation. If too little remains,
publication fails with insufficient-publication-window. The cursor is preserved and later messages
in that folder wait. Operators must investigate the outage and the message disposition; restarting,
resetting the cursor or retrying with a fresh expiry does not resolve that condition. Automatic
abandonment and re-ingestion of expired evidence are not included.

Processing success does not delete the PDF early. Processing failure, quarantine or replay does
not extend its life. When publication might have succeeded but confirmation was lost, the producer
keeps the object and retries with its existing reference. Never delete it merely because there is
no saved publication confirmation. Uploads without events and objects whose Kafka records have
expired receive the same finite storage cleanup, independent of consumer activity.

At the evidence deadline retrieval is denied even if the object still exists. Bucket lifecycle
cleanup deletes bytes asynchronously; the deadline is an access boundary, not an exact physical
erasure time. Soft delete and object versioning are disabled. Minimal user-scoped identity and
terminal-status records remain for the environment lifetime to stop late ingestion recreating
expired attachments. They contain no mail body or PDF bytes, but are still user-linked metadata.
These records are also erased by environment teardown. They are not a permanent audit archive.

Missing, expired, explicitly marked deleted, unauthorized and integrity-failed evidence have
distinct errors. An internal ownership-checked deletion marker suppresses reads and recapture;
it does not claim immediate physical blob erasure or expose a public deletion workflow. Storage outages
are retryable. Capture counters distinguish captured, reused, unsupported, limited and failed work,
including metadata-cleanup failures, without user/source labels. Do not log raw bytes, filenames,
user addresses, arbitrary provider errors or download credentials.

## Local operation

Use the complete producer configuration in [local setup](local.md#run-the-mail-producer).
It explicitly selects filesystem storage and an independent JPA/H2 capture ledger:

```yaml
invoice-watcher:
  attachment-storage:
    mode: filesystem
    filesystem:
      directory: ${user.home}/.local/share/invoice-watcher/attachments
  mail-attachment:
    dao:
      mode: jpa
      jpa:
        data-source:
          basic:
            url: jdbc:h2:file:${user.home}/.local/share/invoice-watcher/mail-attachments
            username: sa
            driver: org.h2.Driver
        ddl-auto: update
        dialect: org.hibernate.dialect.H2Dialect
```

Prepare the parent directory with owner-only permissions before startup:

```shell
install -d -m 700 "$HOME/.local/share/invoice-watcher"
```

The object directory must be absolute, dedicated and private on a local POSIX filesystem.
The backend creates it with mode 0700 and object files with mode 0600. Existing insecure roots,
symlinks, unsupported atomic hard links or a second process using the same root fail startup.
Do not use shared/network storage or let another process modify managed entries. Object references
contain a logical store ID and opaque generation, never an absolute path. Keep the same store ID,
directory and capture database across restarts. Staged files are not readable objects; startup
removes abandoned staging files. Restart and duplicate writes preserve published generations.

While running, cleanup checks expired objects every 60 seconds by default, including uploads
whose events were never confirmed. No cleanup runs while the application is stopped. Reads still
deny expired evidence, and startup resumes cleanup without extending deadlines. Unrelated files
and symlink targets are preserved. Local files are not backed up or replicated by this service.
Keep local Kafka, the capture database and object directory as one evidence stack: resetting only
one does not reset identities or permit recapture. Explicitly disposing of the whole local stack
also removes its suppression metadata and ends its duplicate guarantees.

JPA connection settings belong to the capture capability; selecting user-configuration JPA alone
does not select or configure it. Persistent H2 file storage supports restart recovery. Use
`ddl-auto: update` only for local development; a production SQL backend needs a separately reviewed
schema/migration and credential setup. An optional JDBC password uses the existing encrypted Secret
format, not a clear-text password. Independent named persistence graphs allow capture, processing
and user-configuration JPA to coexist. Do not run two application processes against one H2 file;
use the existing loopback H2 server with distinct database names when necessary.

For a Datastore emulator instead, select the capture DAO independently:

```yaml
invoice-watcher:
  mail-attachment:
    dao:
      mode: firestore
      firestore:
        project-id: synthetic-local-project
        host: http://localhost:8081
```

Use the actual emulator endpoint. Selecting filesystem/JPA initializes no attachment Google
clients or credential discovery. GCS remains an explicit alternative: select storage mode `gcs`,
set `gcs.bucket` to a dedicated private test bucket with the deployed lifecycle policy, and supply
Google Application Default Credentials with capture permissions. Omit the Firestore host only
when intentionally using a real project. Real cloud provisioning is an explicit operator action.
Tests use real private temporary directories, H2 and a Datastore emulator; they do not certify
production IAM or actual asynchronous cloud deletion.
