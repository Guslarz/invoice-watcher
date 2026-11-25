# In action

What a running InvoiceWatcher looks like from the outside: the events it publishes and what it
logs.

!!! note "Illustrative examples"
    The examples on this page are built from the code — the log format is
    taken from a local run, the event from the message model and the JSON serializer. They will
    be replaced with captures from the running environment.

## A published event

One event per picked-up message, on the topic
[`invoice-watcher.mail-producer.task.topic`](../reference/configuration.md#invoice-watcher-mail-producer-task)
(`all-mails` in dev). The key is the user, the value describes the message
([fields](../system/index.md#what-it-produces)):

```text
key:   jan@example.com
value:
```

```json
{
  "eventType": "mail.ingested",
  "schemaVersion": 1,
  "payload": {
    "sourceId": "imap-v1:7eb26f99e274aed76da443ddcdef89475138481c49f8149026f23973ffbba9dc",
    "sender": "Example Shop <billing@shop.example>",
    "title": "Invoice 10/2026",
    "date": "2026-10-13T08:15:00Z",
    "content": "<p>Invoice for October 2026</p>",
    "contentType": "text/html",
    "attachmentManifest": {
      "version": 1,
      "complete": true,
      "limitReason": null,
      "attachments": []
    }
  }
}
```

HTML bodies are preferred in multipart alternatives and preserved without conversion or
sanitization (HTML is untrusted). Related resources, attachments and attached messages are
excluded from the body. PDF attachments are represented separately in attachmentManifest; the
example has no candidates. When no supported body exists, `content` and `contentType` are both `null`; an empty
supported body is published as an empty string with its media type.

The example source ID uses user `jan@example.com`, host `imap.example.com`, port 993,
account `jan.login`, folder `INBOX`, UIDVALIDITY 7 and UID 13. Retries of that occurrence keep
its ID; copies, moves and UIDVALIDITY resets can change it.

This envelope replaces the old flat format on the same topic. Existing consumers must be
updated before rollout; newly published records omit Java class-name headers and are identified
by `eventType` and `schemaVersion`. Historical flat records remain unchanged, lack a guaranteed
source ID, and may lack `contentType`. See [event format migration](../system/index.md#event-format-migration)
for replay and consumer handling.

The all-mails topic has explicit seven-day retention. See [attachment evidence](../setup/mail-attachments.md)
for private object references and the separate evidence deadline.

### Watch the topic

Locally, with Kafka from `.run/files/kafka.yml`:

```shell
docker compose -f .run/files/kafka.yml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic all-mails --from-beginning --property print.key=true
```

In the dev environment, the same tool inside the Kafka pod:

```shell
kubectl -n invoice-watcher-dev exec -it kafka-0 -- \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic all-mails --from-beginning --property print.key=true
```

## What a scan logs

Every unit of work is bracketed with `>` (start) and `<` (end). A scan of one user with one
mailbox and two folders:

```text
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : > Start mail producer task
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : > Processing user: jan@example.com
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : > Scanning for config: imap.example.com
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : > Scanning folder: INBOX
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : < Finished scanning folder: INBOX
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : > Scanning folder: Invoices
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : < Finished scanning folder: Invoices
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : < Finished scanning for config: imap.example.com
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : < Finished processing user: jan@example.com
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : < Finished mail producer task
```

A mailbox that cannot be reached is logged and skipped; the scan goes on with the next one:

```text
ERROR [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : Error scanning mailbox imap.example.com of user jan@example.com
com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException: org.eclipse.angus.mail.util.MailConnectException: Couldn't connect to host, port: imap.example.com, 993; ...
```

A folder that cannot be read is logged once, with the folder, mailbox and user; the remaining
folders of that mailbox wait for the next scan:

```text
ERROR [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : Error scanning folder Invoices of mailbox imap.example.com of user jan@example.com
com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException: jakarta.mail.FolderNotFoundException: Invoices not found
```

A user whose configuration cannot be read is logged as `Error processing user <name>` and the scan
goes on with the next user.

Neither the mailbox username nor the password is logged, the password neither in plain text nor
encrypted.

In the cluster:

```shell
kubectl -n invoice-watcher-dev logs deploy/mail-producer -f
```

## ArgoCD

At `https://argocd.<domain>` the application `invoice-watcher-dev` shows the resources of the
namespace `invoice-watcher-dev`: the `kafka` stateful set with its volume claim, the `mail-producer`
deployment, the Kafka service, the generated config maps, the `app-sa` service account and the image pull secret. It is
*Synced* when the cluster matches `deployments/k8s-apps/environments/dev` at the committed
ArgoCD target revision: master for development, or the selected release tag for a release.

## Processor diagnostics

The shared foundation has synthetic integration fixtures, rather than a deployed recognition
application. Its [processing and recovery behavior](../system/event-processing.md) emits safe
structured fields such as stage, processing identity, attempt and reason:

```text
INFO  Processor stage=fixture processingId=processing-v1:<digest> attempt=1 reason=HANDLER_FAILURE
WARN  Processor stage=fixture failureId=failure-v1:<digest> reason=RETRY_EXHAUSTED
```

These lines illustrate the field layout. Raw tenant keys, message content and exception causes
are excluded. Quarantine retains original bytes in a sensitive recovery destination whose deployment must
enforce access control; these logs contain no original bytes. Review-required results are completed processing outcomes, separately measured
from technical failures. See the [recovery test workflow](../setup/event-processing.md) to exercise
those paths without deploying a processor or using private mail.
