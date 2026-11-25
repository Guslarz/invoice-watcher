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
while it is not needed. ArgoCD reconciles applications from the revision committed in the selected deployment files.

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
- **DEPLOY-042** Before the first environment `up`, the operator configures the Google Auth
  Platform application and its Web application OAuth client once in the Google Cloud console,
  including branding, audience, test users, requested scopes and authorized redirect URIs for
  Google sign-in and Gmail connection. An existing configured app and client are reused.
  The app and client survive environment `down`; `up` neither creates nor replaces them.
  The client ID and AES-encrypted client secret are supplied through the configured GitHub
  secrets, and OpenTofu manages their ephemeral Secret Manager copies. No Google client
  credential is committed to the repository.

### Environment lifecycle

- **DEPLOY-001** The `Environment` workflow (run manually) takes an action, `up` or `down`, and an
  environment (`dev`). Both actions use the deployment files from the Git ref selected for the
  workflow run; there is no separate deployment version input.
- **DEPLOY-002** `up` creates the environment's cloud resources and then installs the cluster
  services. `down` destroys the cloud resources; the cluster and everything in it go with them.
- **DEPLOY-003** Running `up` on an environment that is already up only applies differences.
- **DEPLOY-007** `up` fails before creating any resources when the `AES_KEY`, `PAT` or
  `CLOUDFLARE_API_TOKEN`, `GOOGLE_OAUTH_CLIENT_ID` or `GOOGLE_OAUTH_CLIENT_SECRET` secret is not set.
- **DEPLOY-008** The workflow logs the OpenTofu plan of the action it runs — a destroy plan for
  `down` — and executes exactly that saved plan.
- **DEPLOY-038** `down` leaves no disks of the environment behind.

### Cloud resources (per environment)

- **DEPLOY-010** `up` creates, in the installation's configured region: a GKE cluster with one preemptible
  `e2-standard-2` node, a Firestore database in Datastore mode, Secret Manager secrets (GitHub
  token, Cloudflare token, AES key), a global IP address for the gateway, a 10 GiB persistent
  disk for Kafka, and a proxied wildcard DNS record for the installation's configured domain in Cloudflare pointing at it.
- **DEPLOY-011** The application reaches Firestore through workload identity — no service
  account keys are stored.
- **DEPLOY-012** OpenTofu creates the cluster in the zone and under the name in the checked-in
  installation configuration; `k8s-install` uses the same values to connect to it.
- **DEPLOY-013** The OpenTofu configurations require OpenTofu 1.12 or later. Each root
  configuration pins exact provider versions and commits its `.terraform.lock.hcl`, so CI and
  operators use the same provider builds.

### Cluster services

- **DEPLOY-020** The cluster runs ArgoCD, cert-manager with the Cloudflare origin CA issuer,
  external-secrets, and a GKE gateway serving HTTPS for the installation's configured wildcard domain with a Cloudflare origin
  certificate.
- **DEPLOY-021** ArgoCD is available at `https://argocd.<configured-domain>`.

### Applications

- **DEPLOY-030** ArgoCD keeps the namespace `invoice-watcher-<env>` in sync with
  `deployments/k8s-apps/environments/<env>` at the `targetRevision` committed in its Application
  definition, pruning removed resources and reverting manual changes. Workflow dispatch does not
  derive or override that revision. Development commits target `master`; release commits target
  their corresponding `v<version>` tag.
- **DEPLOY-031** The dev environment runs Kafka (single node; its data is kept on the persistent
  disk from DEPLOY-010, so it survives pod and node restarts, and is deleted by `down`) and the mail
  producer and the registration backend and frontend. Development commits select the `snapshot`
  image, pulled on every start; release commits select the corresponding released image tag.
- **DEPLOY-032** Application configuration is passed as environment variables generated from
  `workloads/<name>/overlays/<env>/config/.env` under `deployments/k8s-apps`.
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
- **DEPLOY-039** The checked-in installation configuration is the source of the non-secret
  repository, registry, domain and location values used by deployment. Its generated configuration
  is committed and must match it.

- **DEPLOY-040** The registration frontend and backend are available at
  https://register.<configured-domain>; the gateway routes frontend assets to the frontend service
  and authentication and API requests to the backend service on that same origin. The mail producer
  remains unexposed.

- **DEPLOY-041** The registration backend obtains the AES key and OIDC client secret from
  Secret Manager through external-secrets. Its workload identity has access to the configured
  user database. No credential values are committed. It exposes liveness and readiness probes
  to Kubernetes, without exposing actuator endpoints through the public gateway.

- **DEPLOY-043** The development environment provisions a dedicated private attachment bucket
  in its configured region with uniform bucket-level access and public access prevention.
  The capture permission role is defined by persistent setup and applied before the first attachment
  rollout; bucket and workload bindings remain ephemeral. Object versioning and soft delete are
  disabled explicitly. Applications authenticate through
  workload identity without stored service-account keys. The producer can create objects and
  reconcile its captures but cannot delete stored evidence; lifecycle cleanup is independent.
  Reading evidence is restricted to explicitly authorized internal workloads and operators.

- **DEPLOY-044** Attachment objects and capture metadata belong to the development environment
  and are deleted by environment down. Replay and duplicate suppression do not survive complete
  teardown. While the environment exists, expired object generations are deleted through a finite
  time-based lifecycle independent of event publication or processing. Teardown and lifecycle
  deletion do not claim an audit archive or an exact physical deletion deadline.

## Acceptance

- **DEPLOY-042** Given an existing Google Auth Platform app and Web application OAuth client,
  when environment `down` and `up` complete, then the app, client ID, client secret and configured
  redirects remain unchanged; only the environment's Secret Manager copies are recreated from
  the supplied GitHub secrets. Given a new installation, when following one-time setup, then
  the operator configures the app and client in the console before running `up`.

- **DEPLOY-001** Given Environment is dispatched on master or a release tag, when up or down
  runs, then each deployment job checks out that workflow run's source SHA.
- **DEPLOY-030** Given a committed Application targetRevision, when cluster-init renders, then
  ArgoCD receives that exact value regardless of the ref used to run Environment.
- **DEPLOY-031** Given a release commit, when its applications render, then mail-producer uses
  the matching release image; given the subsequent development commit, then it uses snapshot.
- **DEPLOY-032** Given the workload overlay's .env file, when dev renders, then the generated
  ConfigMap contains its application configuration and is referenced by the mail-producer pod.

- **DEPLOY-004** Given a new Google Cloud project, when `Environment` runs with `up` / `dev` before
  `persistent` is applied, then the workflow fails to authenticate to Google Cloud; after
  `persistent` is applied and the GitHub variables are set, the same run succeeds.
- **DEPLOY-002** Given the dev environment is down, when `Environment` runs with `up` / `dev`,
  then the workflow succeeds, ArgoCD is reachable at `https://argocd.<configured-domain>` and the
  `mail-producer` pod in `invoice-watcher-dev` is running.
- **DEPLOY-010** Given the dev environment is down, when `Environment` runs with `up` / `dev`, then
  Secret Manager holds `github_pat`, `cloudflare_api_token`, `aes_key` and `google_oauth` and no
  `ghcr_docker_config_json`, and the application still pulls its image.
- **DEPLOY-012** Given a configured non-default zone and cluster name, when `Environment` runs
  with `up` / `dev`, then the cluster is created with those values and `k8s-install` finds it.
- **DEPLOY-039** Given different installation values, when the operator regenerates and commits the
  outputs, deployment uses those values; verification identifies stale generated configuration.
- **DEPLOY-013** Given the committed lock files, when `tofu init` runs in
  `environments/dev/ephemeral` or `environments/dev/persistent`, then it succeeds and leaves
  `.terraform.lock.hcl` unchanged.
- **DEPLOY-030** Given the environment is up, when a change to `deployments/k8s-apps` is merged
  to `master`, then ArgoCD applies it without manual steps.
- **DEPLOY-002** Given the dev environment is up, when `Environment` runs with `down` / `dev`,
  then the cluster, the Firestore database and the DNS record no longer exist.
- **DEPLOY-007** Given the `PAT` secret is not set, when `Environment` runs with `up` / `dev`, then
  the workflow fails before `tofu init`. Same for `AES_KEY`, `CLOUDFLARE_API_TOKEN`, `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET`.
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

- **DEPLOY-031** Given published snapshot images, when application pods restart, then the producer
  and both registration applications pull the snapshot images and become ready.
- **DEPLOY-040** Given an installed environment, when a user visits the registration hostname,
  then sign-in and API requests use HTTPS on that origin and no producer endpoint is reachable.
- **DEPLOY-041** Given configured secret references and workload identity, when the registration
  pod starts, then secrets are injected and probes report readiness; public actuator access fails.

- **DEPLOY-043** Given rendered attachment infrastructure, when validating it, then the bucket
  is private, regional and protected against public access, with versioning and soft delete disabled.
  The producer uses a separate capture identity with no object deletion permission.
- **DEPLOY-044** Given an uploaded object with no retained event, when the evidence window ends,
  then logical access expires and independent lifecycle cleanup applies. Environment down deletes
  the bucket and capture database, without preserving an audit archive.

## Configuration

GitHub repository settings used by the workflows:

| Name | Kind | Meaning |
|------|------|---------|
| `GCP_PROJECT_ID`, `GCP_PROJECT_NUMBER` | variable | Google Cloud project |
| `deployments/installations/dev.json` | file | Repository, registry, domain, region, cluster name and zone |
| `CLOUDFLARE_ZONE_ID` | variable | Cloudflare zone of the domain |
| `PAT` | secret | GitHub token used to pull images and read the repository |
| `CLOUDFLARE_API_TOKEN` | secret | Cloudflare token for DNS and origin certificates |
| `GOOGLE_OAUTH_CLIENT_ID` | secret | Google web OAuth client identifier |
| `GOOGLE_OAUTH_CLIENT_SECRET` | secret | OAuth client secret encrypted with the application AES key |
| `AES_KEY` | secret | AES key that decrypts mailbox passwords, as printed by `secret-helper generate-key` |

## Out of scope

- Environments other than `dev`.
- Exposing the mail producer over HTTP.
- Backups: `down` deletes the Firestore data.
