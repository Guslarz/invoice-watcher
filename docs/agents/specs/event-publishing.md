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
- **EVENT-004** The event value is a JSON object with the fields:

    | Field | Type | Content |
    |-------|------|---------|
    | `sender` | string | The `From` address, e.g. `Shop <billing@shop.example>` |
    | `title` | string | Subject |
    | `date` | date-time | When the message was sent, in UTC |
    | `content` | string | Message content *(always `null` for now — see MAIL-030)* |

- **EVENT-010** The event backend is selected with `invoice-watcher.event-producer.mode`. The only
  supported value is `kafka`; with it, events are sent to the brokers in
  `invoice-watcher.event-producer.kafka.bootstrap-servers`, keys as strings, values as JSON.
  `invoice-watcher.event-producer.kafka.bootstrap-servers` is required in `kafka` mode. A missing
  or unknown value fails the startup with an error naming the property.
- **EVENT-011** A message is considered handed over once the event backend confirms it has stored
  the event. The folder's scan position is saved once, after all messages of the folder are handed
  over.
- **EVENT-012** If the event backend does not confirm an event, handing over fails, the folder's
  position is saved at the last confirmed event and the folder is treated as failed
  ([MAIL-041](../specs/mail-scanning.md)). An event can still be published twice, e.g. when the
  application stops before the position is saved: delivery is at-least-once, consumers must
  tolerate duplicates.
- **EVENT-013** A missing required event property fails the startup and names the property.

## Acceptance

- **EVENT-001** Given a folder with three new messages, when it is scanned, then three events
  appear on the topic, in the order the messages were received.
- **EVENT-003** Given user `jan@example.com`, when one of their messages is published, then the
  event key is `jan@example.com`.
- **EVENT-004** Given a message from `billing@shop.example` with subject `Invoice 10/2026`, when it
  is published, then the event value has `sender` containing `billing@shop.example` and `title`
  `Invoice 10/2026`.
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

## Configuration

Properties, their types, defaults and meaning are listed in the generated reference:

- [`invoice-watcher.mail-producer.task`](../../reference/configuration.md#invoice-watcher-mail-producer-task)
- [`invoice-watcher.event-producer`](../../reference/configuration.md#invoice-watcher-event-producer)
- [`invoice-watcher.event-producer.kafka`](../../reference/configuration.md#invoice-watcher-event-producer-kafka)

## Out of scope

- Consuming events and recognizing invoices.
- Exactly-once delivery: consumers must tolerate duplicates.
