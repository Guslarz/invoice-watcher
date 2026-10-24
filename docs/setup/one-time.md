# One-time setup

These steps are done once per Google Cloud project, by hand. After them, the environment is
created and destroyed only by the [`Environment` workflow](environment.md).

The GitHub workflows cannot create what they themselves need to run, so a small OpenTofu
configuration, `persistent` (`deployments/infrastructure/environments/<env>/persistent`), is
applied once with your own Google Cloud credentials. It creates resources that outlive the
environment and that `up` and `down` never touch:

- the bucket `<project-id>-tfstate` (versioned, not public) for the OpenTofu state of the
  environment,
- the IAM, IAM Credentials and Cloud Resource Manager APIs,
- the service account `github-actions` with the roles needed to create and destroy the
  environment, and the workload identity pool `github-actions-pool` with provider
  `github-provider`, which lets only workflows of your GitHub repository act as that account —
  without stored keys.

The OpenTofu state of `persistent` itself stays on your machine, not in git and not in the
bucket.

## 1. Apply `persistent`

Log in with the account that is Owner of the project. OpenTofu uses the application default
credentials:

```shell
gcloud auth login
gcloud auth application-default login
```

Create `deployments/infrastructure/environments/dev/persistent/terraform.tfvars` (all `*.tfvars`
files are git-ignored):

```hcl
gcp_project_id = "<project-id>"
```

Apply it:

```shell
cd deployments/infrastructure/environments/dev/persistent
tofu init
tofu apply
```

!!! danger "Keep the state file"
    The state of `persistent` is only on your machine (`terraform.tfstate` in that directory,
    git-ignored). Back it up. Without it OpenTofu no longer knows these resources, and changing
    them means importing them first.

To check the result, look for the bucket `<project-id>-tfstate` in Cloud Storage and the
service account `github-actions@<project-id>.iam.gserviceaccount.com` in IAM.

Note the project number — GitHub needs it to find the workload identity pool:

```shell
gcloud projects describe <project-id> --format='value(projectNumber)'
```

## 2. Create a Cloudflare API token

The token is used by OpenTofu to create the DNS record and by the cluster to issue origin
certificates.

1. In the Cloudflare dashboard open **My Profile → API Tokens → Create Token → Create Custom
   Token**.
2. Permissions:

    | Scope | Permission | Access | Used by |
    |-------|------------|--------|---------|
    | Zone | DNS | Edit | OpenTofu: wildcard record `*.<domain>` |
    | Zone | SSL and Certificates | Edit | origin-ca-issuer: origin certificate for the gateway |

3. Zone Resources: **Include → Specific zone → `<domain>`**.
4. Create the token and copy it — it is shown only once.

The **Zone ID** is on the zone's **Overview** page, in the API section on the right.

## 3. Create a GitHub personal access token

The token lets the cluster read the repository (ArgoCD) and pull images from `ghcr.io`. GitHub
packages accept only classic tokens:

1. **Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new
   token (classic)**.
2. Scopes:

    | Scope | Used by |
    |-------|---------|
    | `repo` | ArgoCD reading the (private) repository |
    | `read:packages` | Pulling the images from `ghcr.io` |

3. Choose an expiration and copy the token. When it expires, create a new one, update the `PAT`
   secret and run `Environment` with `up` again.

## 4. Configure the GitHub repository

In the repository open **Settings → Secrets and variables → Actions** and add:

| Name | Kind | Value |
|------|------|-------|
| `GCP_PROJECT_ID` | variable | `<project-id>` |
| `GCP_PROJECT_NUMBER` | variable | the number printed in step 1 |
| `GCP_GKE_CLUSTER_NAME` | variable | the cluster name, e.g. `invoice-watcher-cluster` — OpenTofu creates the cluster under it and `k8s-install` looks for it there |
| `GCP_ZONE` | variable | a zone in `europe-west1`, e.g. `europe-west1-b` — OpenTofu creates the cluster in it and `k8s-install` looks for it there |
| `CLOUDFLARE_ZONE_ID` | variable | the Zone ID from step 2 |
| `PAT` | secret | the token from step 3 |
| `CLOUDFLARE_API_TOKEN` | secret | the token from step 2 |
| `AES_KEY` | secret | a new key from `secret-helper generate-key` ([keys and users](keys-and-users.md#1-generate-the-key)) |

The `Snapshot` and `Release` workflows need nothing else — they publish images with the
workflow's own `GITHUB_TOKEN`.

Next: [create the environment](environment.md).
