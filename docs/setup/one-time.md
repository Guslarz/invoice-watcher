# One-time setup

These steps are done once per Google Cloud project, by hand. After them, the environment is
created and destroyed only by the [`Environment` workflow](environment.md).

--8<-- "docs/specs/deployment.md:one-time-setup"

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

--8<-- "docs/specs/deployment.md:github-settings"

Where the values come from:

| Name | Value |
|------|-------|
| `GCP_PROJECT_ID` | `<project-id>` |
| `GCP_PROJECT_NUMBER` | the number printed in step 1 |
| `GCP_GKE_CLUSTER_NAME` | `invoice-watcher-cluster` (`gcp_gke_cluster_name` in the `ephemereal` variables) |
| `GCP_ZONE` | `europe-west1-b` (`gcp_zone` in the `ephemereal` variables) |
| `CLOUDFLARE_ZONE_ID` | the Zone ID from step 2 |
| `PAT` | the token from step 3 |
| `CLOUDFLARE_API_TOKEN` | the token from step 2 |

The `Snapshot` and `Release` workflows need nothing else — they publish images with the
workflow's own `GITHUB_TOKEN`.

Next: [create the environment](environment.md).
