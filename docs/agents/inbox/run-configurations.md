---
title: Fix broken IntelliJ run configurations
kind: bug
area: build
added: 2026-10-24
---

`.run/`:
- `SecretHelperApp - encrypt.run.xml:3` uses module `invoice-watcher.apps.secret-helper.main`; the
  others use `invoice-watcher.apps.secret-helper-app.main`, so the encrypt configuration cannot run.
- `local.run.xml:3` runs `files/kafka.yml: Compose Deployment`, but the compose configuration in
  `kafka.run.xml` is named `kafka` (verify in IntelliJ).
- `.run/files/kafka.yml` duplicates the environment and image version of
  `deployments/k8s-apps/apps/kafka/overlays/dev/config/.env`.
- `.gitignore:47` ignores `.run/tmp`, which nothing uses.
