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
| [yamllint](https://yamllint.readthedocs.io/) | 1.37.x | Optional: checking the YAML style of the deployment files |

`scripts/check-deployments.sh` (part of `scripts/verify.sh`) skips the checks whose tool is
missing; CI installs every tool, and there a missing one fails the check.

## Values to adapt in the repository

Edit `deployments/installations/dev.json` for your fork: set `repository` to `owner/name`,
`registry_username` to the owner of the PAT used to read the repository and pull images, and set
`domain`, `region`, `zone` and `cluster_name` for your installation. The image is published as
`ghcr.io/<owner>/<repository>/mail-producer`. The zone must belong to the region. The file contains
no secrets; project IDs, Cloudflare zone ID and credentials remain in external settings.

Regenerate and commit the outputs alongside the installation file:

```shell
python3 scripts/generate-installation.py
python3 scripts/generate-installation.py --check
```

Do this before applying the one-time setup. CI and `scripts/verify.sh` reject stale outputs.
After infrastructure or bootstrap values change, apply `persistent` again if its values changed,
and rerun `Environment` with `up` after merging the generated changes.

On the pages that follow, `<project-id>` and `<domain>` stand for your values.
