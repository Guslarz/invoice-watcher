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

<!-- --8<-- [start:event] -->
- **EVENT-001** Each picked-up message is published as exactly one event, in the order the
  messages are stored in the folder (oldest first).
- **EVENT-002** All events go to one topic, configured by
  `invoice-watcher.mail-producer.task.topic` (`all-mails` in the dev environment).
- **EVENT-003** The event key is the name of the user who owns the mailbox (their e-mail
  address), so all events of one user land in the same partition, in order.
- **EVENT-004** The event value is a JSON object with the fields:

    | Field | Type | Content |
    |-------|------|---------|
    | `sender` | string | The `From` address, e.g. `Shop <billing@shop.example>` |
    | `title` | string | Subject |
    | `date` | date-time | When the message was sent, in UTC |
    | `content` | string | Message content *(always `null` for now — see MAIL-030)* |
<!-- --8<-- [end:event] -->

- **EVENT-010** The event backend is selected with `invoice-watcher.event-producer.mode`. The only
  supported value is `kafka`; with it, events are sent to the brokers in
  `invoice-watcher.event-producer.kafka.bootstrap-servers`, keys as strings, values as JSON.
- **EVENT-011** A message is considered handed over once it is passed to the event backend; the
  folder's scan timestamp moves forward after all messages of the folder are handed over.
  *(gap: delivery failures are not detected —
  `docs/agents/inbox/kafka-send-not-confirmed.md`)*

## Acceptance

- **EVENT-001** Given a folder with three new messages, when it is scanned, then three events
  appear on the topic, in the order the messages were received.
- **EVENT-003** Given user `jan@example.com`, when one of their messages is published, then the
  event key is `jan@example.com`.
- **EVENT-004** Given a message from `billing@shop.example` with subject `Invoice 10/2026`, when it
  is published, then the event value has `sender` containing `billing@shop.example` and `title`
  `Invoice 10/2026`.

## Configuration

| Property | Default | Meaning |
|----------|---------|---------|
| `invoice-watcher.mail-producer.task.topic` | — | Topic for mail events |
| `invoice-watcher.event-producer.mode` | — | Event backend: `kafka` |
| `invoice-watcher.event-producer.kafka.bootstrap-servers` | — | Kafka brokers, e.g. `kafka:9092` |

## Out of scope

- Consuming events and recognizing invoices.
- Exactly-once delivery: consumers must tolerate duplicates.
