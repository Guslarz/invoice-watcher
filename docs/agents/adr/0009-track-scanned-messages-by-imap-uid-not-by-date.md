---
id: ADR-0009
status: accepted
date: 2026-10-24
---

# ADR-0009: Track scanned messages by IMAP UID, not by date

## Context

Each folder kept a scan timestamp compared with the messages' sent dates, found by a binary search
over the folder in IMAP order. This failed in several ways:

- Sent dates are set by senders. They are not ordered within a folder, so a message that arrives
  late with an old date is skipped, and a binary search over them is not well defined.
- IMAP `SEARCH SINCE` / `SENTSINCE` compare by day, not by second.
- A date cursor cannot both avoid duplicates and avoid skipping messages: moving it past the newest
  date skips other messages with the same date, keeping it at that date publishes them again.

IMAP UIDs are assigned by the server in arrival order, strictly ascending and unique within a
folder's UIDVALIDITY.

## Decision

- A folder's scan position is its UIDVALIDITY and the UID of the last handed-over message. A scan
  picks up the messages with a higher UID, in UID order.
- When the server reports a different UIDVALIDITY, discard the position and scan the folder as never
  scanned.
- A folder without a position is scanned from its start date by the server's received date
  (INTERNALDATE), filtered to the second locally, or completely without a start date.
- Save the position once per folder: after all its events are confirmed, or at the last confirmed
  event when delivery fails.

## Consequences

- Each message is published once in normal operation, regardless of its sent date.
- Duplicates remain possible (the application stops between a confirmed event and saving the
  position); delivery stays at-least-once.
- A UIDVALIDITY change re-publishes the folder from its start date.
- The mail connector requires folders that support UIDs; IMAP folders do.
