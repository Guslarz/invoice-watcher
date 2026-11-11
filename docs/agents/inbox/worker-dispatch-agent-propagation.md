---
title: Implicitly propagate the worker agent during dispatch
kind: idea
area: build
added: 2026-11-11
status: later
size: M
blocked-by: []
---

During dispatch, the agent intended for the worker should be implicitly passed to
`scripts/worker.py`, which should forward it to the selected backend. Agent selection is distinct
from backend and model selection; dispatch should not silently fall back to a different agent.

Investigate how to resolve the intended agent from the dispatch context and map it to each
backend's agent-selection mechanism, including what to do when the agent is unavailable or the
backend does not support it. Update the dispatch workflow and add forwarding tests when this is
implemented. See [Protocol parallel](../protocols/parallel.md).
