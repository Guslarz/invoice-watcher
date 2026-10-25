---
title: Configuration reference shows enum types as binary class names
kind: idea
area: docs
added: 2026-10-25
---

`scripts/gen-reference.py` (`short_type`) renders nested enum types as their binary name, e.g.
`EventProducerProperties$Mode` for `invoice-watcher.event-producer.mode`. For users, the
supported values (`kafka`, or `firestore` / `jpa`) would be more useful in the Type column, e.g.
taken from the metadata `hints` or the enum constants in lowercase.
