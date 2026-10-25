# Prerequisites

This section takes you from nothing to a running environment. Everything that is not automated
is described here, in this order:

1. **Prerequisites** (this page) — accounts, tools and the values to adapt in the repository.
2. [One-time setup](one-time.md) — the `persistent` OpenTofu configuration, tokens, GitHub
   settings.
3. [Environment](environment.md) — creating and destroying the dev environment.
4. [Keys and users](keys-and-users.md) — the AES key, encrypted passwords and users.

[Local development](local.md) needs only the tools below, no cloud accounts.

## Accounts

| Account | What you need | Used for |
|---------|---------------|----------|
| Google Cloud | A project with a billing account linked, and a user with the **Owner** role on it | GKE, Firestore, Secret Manager, the state bucket |
| Cloudflare | A domain added as a zone (its name servers pointed at Cloudflare) | DNS for `*.<domain>`, origin certificates |
| GitHub | The repository (a fork or a copy) with Actions enabled | CI, images on `ghcr.io`, the `Environment` workflow |

Cloudflare must talk to the cluster over HTTPS: in the zone, set **SSL/TLS → Overview →
encryption mode** to **Full (strict)**. The cluster gateway listens only on port 443 with a
Cloudflare origin certificate, so the *Flexible* mode does not work.

## Tools

| Tool | Version | Needed for |
|------|---------|------------|
| JDK | 25 | Building and running the applications |
| Docker | any recent | Local Kafka, building images |
| Python | 3.10+ | The documentation site |
| [OpenTofu](https://opentofu.org/docs/intro/install/) | 1.12.x | The one-time `persistent` setup; checking the OpenTofu files |
| [gcloud CLI](https://cloud.google.com/sdk/docs/install) | any recent | Authenticating OpenTofu, inspecting the cluster |
| kubectl | any recent | Optional: inspecting the cluster (`gcloud components install kubectl`); rendering the kustomize environments |
| [helmfile](https://helmfile.readthedocs.io/) + [helm](https://helm.sh/docs/intro/install/) | 1.7.x / 4.2.x | Optional: rendering the helmfile |
| [kubeconform](https://github.com/yannh/kubeconform) | 0.8.x | Optional: validating the rendered manifests |

`scripts/check-deployments.sh` (part of `scripts/verify.sh`) skips the checks whose tool is
missing; CI installs every tool, and there a missing one fails the check.

## Values to adapt in the repository

The repository is set up for one installation: GitHub user `Guslarz`, domain `guslarz.com`,
region `europe-west1`. To reproduce it under another account or domain, change these values
(everything else comes from GitHub variables and secrets):

| Value | Where |
|-------|-------|
| GitHub repository `owner/name` | `deployments/infrastructure/environments/dev/persistent/variables.tf` (`github_repository`) |
| GitHub user name | `deployments/k8s/values/cluster-init.yml.gotmpl` (`github.username`, `github.orgUrl`), `deployments/k8s-apps/components/app-identity/image-pull-secret.yml` |
| Image name `ghcr.io/<owner>/invoice-watcher/mail-producer-docker` | `deployments/k8s-apps/apps/mail-producer/overlays/dev/kustomization.yml` |
| Domain | `deployments/infrastructure/environments/dev/ephemeral/variables.tf` (`domain_name`), `deployments/k8s/environments/dev.yml.gotmpl` (`domain`) |
| Region | `deployments/infrastructure/environments/dev/ephemeral/variables.tf` (`gcp_region`) |
| Zone, cluster name | the `GCP_ZONE` and `GCP_GKE_CLUSTER_NAME` GitHub variables ([one-time setup](one-time.md#4-configure-the-github-repository)); `gcp_zone` and `gcp_gke_cluster_name` in the same `variables.tf` are used only when you run OpenTofu locally |

On the pages that follow, `<project-id>` and `<domain>` stand for your values.
