---
id: SPEC-DEPLOY
title: Deployment
status: implemented
audience: [operator]
modules:
  - deployments/infrastructure
  - deployments/k8s
  - deployments/k8s-apps
  - images/mail-producer-docker
---

# Deployment

## Purpose

InvoiceWatcher runs on Google Kubernetes Engine. An environment can be created and destroyed
from GitHub Actions with one workflow (after a one-time manual setup), so that it costs nothing
while it is not needed. After it is up, application changes are deployed automatically from `master`.

## Behavior

### One-time setup

- **DEPLOY-004** Before the first `up`, the `persistent` configuration
  (`deployments/infrastructure/environments/<env>/persistent`) must be applied once, by hand,
  with OpenTofu and the operator's own Google Cloud credentials. The workflows cannot do it,
  because it creates what they need to run.
- **DEPLOY-005** `persistent` creates resources that outlive the environment and are not touched
  by `up` or `down`:
    - the bucket `<project-id>-tfstate` (versioned, not public) holding the OpenTofu state of the
      environment,
    - the IAM, IAM Credentials and Cloud Resource Manager APIs,
    - the service account `github-actions` with the roles it needs to create and destroy the
      environment, and the workload identity pool `github-actions-pool` with provider
      `github-provider`, which lets only workflows of the configured GitHub repository act as
      that account — without stored keys.
- **DEPLOY-006** The OpenTofu state of `persistent` itself is kept locally on the operator's
  machine (not in git and not in the bucket).

### Environment lifecycle

- **DEPLOY-001** The `Environment` workflow (run manually) takes an action, `up` or `down`, and an
  environment (`dev`).
- **DEPLOY-002** `up` creates the environment's cloud resources and then installs the cluster
  services. `down` destroys the cloud resources; the cluster and everything in it go with them.
- **DEPLOY-003** Running `up` on an environment that is already up only applies differences.
- **DEPLOY-007** `up` fails before creating any resources when the `AES_KEY`, `PAT` or
  `CLOUDFLARE_API_TOKEN` secret is not set.
- **DEPLOY-008** The workflow logs the OpenTofu plan of the action it runs — a destroy plan for
  `down` — and executes exactly that saved plan.
- **DEPLOY-038** `down` leaves no disks of the environment behind.

### Cloud resources (per environment)

- **DEPLOY-010** `up` creates, in region `europe-west1`: a GKE cluster with one preemptible
  `e2-standard-2` node, a Firestore database in Datastore mode, Secret Manager secrets (GitHub
  token, Cloudflare token, AES key), a global IP address for the gateway, a 10 GiB persistent
  disk for Kafka, and a proxied wildcard DNS record `*.guslarz.com` in Cloudflare pointing at it.
- **DEPLOY-011** The application reaches Firestore through workload identity — no service
  account keys are stored.
- **DEPLOY-012** OpenTofu creates the cluster in the zone and under the name given by the
  repository variables `GCP_ZONE` and `GCP_GKE_CLUSTER_NAME`, the same ones `k8s-install` uses.
- **DEPLOY-013** The OpenTofu configurations require OpenTofu 1.12 or later. Each root
  configuration pins exact provider versions and commits its `.terraform.lock.hcl`, so CI and
  operators use the same provider builds.

### Cluster services

- **DEPLOY-020** The cluster runs ArgoCD, cert-manager with the Cloudflare origin CA issuer,
  external-secrets, and a GKE gateway serving HTTPS for `*.guslarz.com` with a Cloudflare origin
  certificate.
- **DEPLOY-021** ArgoCD is available at `https://argocd.guslarz.com`.

### Applications

- **DEPLOY-030** ArgoCD keeps the namespace `invoice-watcher-<env>` in sync with
  `deployments/k8s-apps/environments/<env>` on `master`, pruning removed resources and reverting
  manual changes.
- **DEPLOY-031** The dev environment runs Kafka (single node; its data is kept on the persistent
  disk from DEPLOY-010, so it survives pod and node restarts, and is deleted by `down`) and the mail
  producer using the image tagged `snapshot`, pulled on every start, so restarting the pod picks
  up the latest `master` build.
- **DEPLOY-032** Application configuration is passed as environment variables generated from
  `overlays/<env>/config/.env` of each application.
- **DEPLOY-033** Images are pulled from `ghcr.io` with credentials synced from Secret Manager.
- **DEPLOY-034** The mail producer gets the AES key (`invoice-watcher.secret.aes.key`) from the
  Secret Manager secret `aes_key`, synced into its namespace by external-secrets and passed as an
  environment variable. The key is not stored in the repository.
- **DEPLOY-035** Every application container declares CPU and memory requests and a memory limit;
  no CPU limit is set.
- **DEPLOY-036** The mail producer reports liveness and readiness through the Spring Boot actuator
  health probes; Kubernetes restarts it when liveness fails.
- **DEPLOY-037** Kafka is ready when its client port accepts connections and is restarted when it
  stops accepting them.

## Acceptance

- **DEPLOY-004** Given a new Google Cloud project, when `Environment` runs with `up` / `dev` before
  `persistent` is applied, then the workflow fails to authenticate to Google Cloud; after
  `persistent` is applied and the GitHub variables are set, the same run succeeds.
- **DEPLOY-002** Given the dev environment is down, when `Environment` runs with `up` / `dev`,
  then the workflow succeeds, ArgoCD is reachable at `https://argocd.guslarz.com` and the
  `mail-producer` pod in `invoice-watcher-dev` is running.
- **DEPLOY-010** Given the dev environment is down, when `Environment` runs with `up` / `dev`, then
  Secret Manager holds `github_pat`, `cloudflare_api_token` and `aes_key` and no
  `ghcr_docker_config_json`, and the application still pulls its image.
- **DEPLOY-012** Given `GCP_ZONE` set to a zone other than the OpenTofu default, when `Environment`
  runs with `up` / `dev`, then the cluster is created in that zone and `k8s-install` finds it.
- **DEPLOY-013** Given the committed lock files, when `tofu init` runs in
  `environments/dev/ephemeral` or `environments/dev/persistent`, then it succeeds and leaves
  `.terraform.lock.hcl` unchanged.
- **DEPLOY-030** Given the environment is up, when a change to `deployments/k8s-apps` is merged
  to `master`, then ArgoCD applies it without manual steps.
- **DEPLOY-002** Given the dev environment is up, when `Environment` runs with `down` / `dev`,
  then the cluster, the Firestore database and the DNS record no longer exist.
- **DEPLOY-007** Given the `PAT` secret is not set, when `Environment` runs with `up` / `dev`, then
  the workflow fails before `tofu init`. Same for `AES_KEY` and `CLOUDFLARE_API_TOKEN`.
- **DEPLOY-008** Given the dev environment is up, when `Environment` runs with `down` / `dev`, then
  the logged plan lists only destroys and the next step applies that saved plan file.
- **DEPLOY-034** Given the `AES_KEY` secret is set and the dev environment is up, when the
  `mail-producer` pod starts, then it starts with the key from Secret Manager, and no file under
  `deployments/` contains the key.
- **DEPLOY-035** Given the dev environment is up, when `kubectl get pods -o yaml` runs in
  `invoice-watcher-dev`, then every container has `resources.requests.cpu`,
  `resources.requests.memory` and `resources.limits.memory`, and no `resources.limits.cpu`.
- **DEPLOY-036** Given the mail producer is running, when `/actuator/health/liveness` stops
  answering, then the pod is restarted; while it starts, the pod is not Ready.
- **DEPLOY-031** Given an event published to `all-mails`, when the Kafka pod is deleted and comes
  back, then the event can still be consumed from the beginning of the topic.
- **DEPLOY-038** Given the environment is up, when `down` finishes, then
  `gcloud compute disks list` shows no disk of the environment.
- **DEPLOY-037** Given Kafka is starting, when port 9092 does not accept connections yet, then the
  pod is not Ready.

## Configuration

GitHub repository settings used by the workflows:

| Name | Kind | Meaning |
|------|------|---------|
| `GCP_PROJECT_ID`, `GCP_PROJECT_NUMBER` | variable | Google Cloud project |
| `GCP_GKE_CLUSTER_NAME`, `GCP_ZONE` | variable | Cluster name and zone |
| `CLOUDFLARE_ZONE_ID` | variable | Cloudflare zone of the domain |
| `PAT` | secret | GitHub token used to pull images and read the repository |
| `CLOUDFLARE_API_TOKEN` | secret | Cloudflare token for DNS and origin certificates |
| `AES_KEY` | secret | AES key that decrypts mailbox passwords, as printed by `secret-helper generate-key` |

## Out of scope

- Environments other than `dev`.
- Exposing the mail producer over HTTP.
- Backups: `down` deletes the Firestore data.
