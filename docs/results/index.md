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
  "sender": "Example Shop <billing@shop.example>",
  "title": "Invoice 10/2026",
  "date": "2026-10-13T08:15:00Z",
  "content": null
}
```

`content` is always `null` for now — reading message bodies is a known gap (MAIL-030).

### Watch the topic

Locally, with Kafka from `.run/files/kafka.yml`:

```shell
docker compose -f .run/files/kafka.yml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic all-mails --from-beginning --property print.key=true
```

In the dev environment, the same tool inside the Kafka pod:

```shell
kubectl -n invoice-watcher-dev exec -it deploy/kafka -- \
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

A mailbox that cannot be reached is logged and skipped; the scan goes on with the next one
(MAIL-040):

```text
ERROR [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : Error processing scan config: MailScanConfig[server=MailServer[host=imap.example.com, port=993], ...]
com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException: org.eclipse.angus.mail.util.MailConnectException: Couldn't connect to host, port: imap.example.com, 993; ...
INFO  [scheduling-1] c.g.i.m.task.MailProducerTaskImpl : < Finished scanning for config: imap.example.com
```

The password is never logged, neither in plain text nor encrypted.

In the cluster:

```shell
kubectl -n invoice-watcher-dev logs deploy/mail-producer -f
```

## ArgoCD

At `https://argocd.<domain>` the application `invoice-watcher-dev` shows the resources of the
namespace `invoice-watcher-dev`: the `kafka` and `mail-producer` deployments, the Kafka service,
the generated config maps, the `app-sa` service account and the image pull secret. It is
*Synced* when the cluster matches `deployments/k8s-apps/environments/dev` on `master`.
