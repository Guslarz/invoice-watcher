---
title: AES key committed in plain text in the dev overlay
kind: bug
area: secrets
added: 2026-10-13
---

`deployments/k8s-apps/apps/mail-producer/overlays/dev/config/.env` contains
`INVOICE_WATCHER_SECRET_AES_KEY` in plain text. Mail credentials encrypted with it are only as safe
as access to this repository — must be fixed (and the key rotated) before the repository is
made public. Move the key to GCP Secret Manager and sync it with external-secrets
(already installed by `deployments/k8s`), then rotate the key and re-encrypt stored credentials.
