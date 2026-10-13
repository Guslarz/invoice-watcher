---
title: Define what the published docs cover
kind: idea
area: docs
added: 2026-10-13
---

The GitHub Pages site should describe the system so that someone else can understand it and
reproduce it, not be a set of step-by-step how-tos. It should cover:
- **The system**: what InvoiceWatcher does, its architecture (applications, libraries, Kafka,
  storage, GKE), and how the parts fit together.
- **Manual steps**: everything done by hand outside the automated workflows, so that the
  deployment and the repository setup can be reproduced, e.g. the one-time `persistent`
  Terraform environment, GitHub secrets and variables, Cloudflare and GCP setup, generating the
  AES key and encrypting mailbox passwords with `secret-helper-app`, creating a user.
- **All configuration properties**, with defaults and meaning (the generated reference,
  `docs/reference/`).
- **Actual usage and visible results**: things worth showing, e.g. what a published event looks
  like, ArgoCD with the running applications, what a scan logs.

Points to settle when planning the guides (`docs/guide/`, `docs/operate/`, `docs/develop/`):
- Whether the guide/operate/develop split by audience still fits this content, or the site
  should be organised as system → setup → configuration → results.
- How the normative, terse specs (`docs/specs/`) sit next to the descriptive pages (ADR-0001
  publishes them as-is); prefer linking to spec clauses over restating them.
