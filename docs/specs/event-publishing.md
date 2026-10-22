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
<!-- --8<-- [start:delivery] -->
- **EVENT-011** A message is considered handed over once the event backend confirms it has stored
  the event; the folder's scan timestamp moves forward only after all messages of the folder are
  handed over.
- **EVENT-012** If the event backend does not confirm an event, handing over fails and the folder
  is treated as failed ([MAIL-041](../specs/mail-scanning.md)). Events of the same folder
  confirmed before the failure are published again in the next scan: delivery is at-least-once,
  consumers must tolerate duplicates.
<!-- --8<-- [end:delivery] -->

## Acceptance

- **EVENT-001** Given a folder with three new messages, when it is scanned, then three events
  appear on the topic, in the order the messages were received.
- **EVENT-003** Given user `jan@example.com`, when one of their messages is published, then the
  event key is `jan@example.com`.
- **EVENT-004** Given a message from `billing@shop.example` with subject `Invoice 10/2026`, when it
  is published, then the event value has `sender` containing `billing@shop.example` and `title`
  `Invoice 10/2026`.
- **EVENT-011** Given the backend confirms every event, when a folder with two new messages is
  scanned, then the folder's scan timestamp moves forward.
- **EVENT-012** Given the backend rejects the second of three events, when the folder is scanned,
  then an error is logged, the folder's scan timestamp is unchanged and the remaining folders of
  the mailbox are skipped.

## Configuration

Properties, their types, defaults and meaning are listed in the generated reference:

- [`invoice-watcher.mail-producer.task`](../reference/configuration.md#invoice-watcher-mail-producer-task)
- [`invoice-watcher.event-producer`](../reference/configuration.md#invoice-watcher-event-producer)
- [`invoice-watcher.event-producer.kafka`](../reference/configuration.md#invoice-watcher-event-producer-kafka)

## Out of scope

- Consuming events and recognizing invoices.
- Exactly-once delivery: consumers must tolerate duplicates.
