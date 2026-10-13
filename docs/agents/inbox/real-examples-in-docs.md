---
title: Replace illustrative examples in the docs with captures from the running system
kind: idea
area: docs
added: 2026-10-13
---

`docs/results/index.md` shows a published event, scan logs and the ArgoCD view as illustrative
examples built from the code (marked with a note on the page). Once the dev environment runs with
real users, replace them with captures from the running system:
- a real event from the `all-mails` topic (anonymised),
- scan log lines from `kubectl logs deploy/mail-producer`,
- screenshots: ArgoCD application view, GKE workloads, Firestore entities, Cloudflare DNS record.

Then remove the "Illustrative examples" note. Screenshots go next to the page (e.g.
`docs/results/img/`).
