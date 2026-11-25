---
title: Bound MIME traversal and decoded body reads during body selection
kind: tech-debt
area: mail-scanning
added: 2026-11-24
status: later
size: M
blocked-by: []
---

Body selection in MimeBodyReader recursively walks MIME containers and decodes the selected text
before the connector invokes the attachment capture callback. The attachment depth, byte and time
limits therefore do not bound this earlier work. Deep nesting, many unsupported body parts or a
large decoded body can consume excessive stack, memory or scan time independently of PDF capture.

Plan explicit body traversal and decoded-size/time limits, including visible outcomes and cursor
semantics, without silently changing which supported body is selected. Cover adversarial nesting,
large bodies and folders with earlier confirmed messages. This is separate from attachment storage
and the much later consideration of malware scanning.
