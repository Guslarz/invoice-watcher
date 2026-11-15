---
title: Move registration sessions and OAuth flow state outside application pods before scaling
kind: tech-debt
area: deployment
added: 2026-11-12
status: later
size: M
blocked-by: []
---

Registration currently uses in-memory HttpSession state, one backend replica and Recreate rollout.
Pod replacement loses browser sessions and pending Google consent flows; additional replicas cannot
handle each other's sessions. Address this before horizontal scaling or requiring sign-in and
consent to survive pod replacement. See ADR-0018 and the registration capability.

Keep secure HttpOnly cookies and backend-held credentials as the starting design. Evaluate shared
sessions through Spring Session (Redis or JDBC), including operational cost and fit with the dev
Firestore and local H2 environments. Decide expiry, logout/invalidation, store availability and data
retention. A browser JWT alone does not resolve pending OAuth state, nonce or consent ownership.

Include the security context, authorization requests/state, pending connection version and pending
encrypted grant in the shared-state design. Audit serialization and encryption: the current Flow,
PendingGrant and Secret implementations are not ready to be placed unchanged into a serialized
session store. Store encrypted grant values or references, never the runtime AES key held by a
Secret object; reconstruct wrappers using the runtime key. Keep token values out of logs and
preserve single-use callback and CSRF guarantees.

Test login on replica A with callbacks/API requests on replica B, pod termination during consent,
concurrent requests, expiry and logout across replicas. Enable rolling/scaled deployments only
after those checks pass; sticky routing alone does not preserve state when a pod disappears.
