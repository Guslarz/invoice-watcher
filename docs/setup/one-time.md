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

You need OpenTofu 1.12 or later. The provider versions come from the committed
`.terraform.lock.hcl`, so you get the same builds as the workflows.

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

## Configure Google sign-in and Gmail

Before the first environment `up`, configure the Google Auth Platform app and Web application
OAuth client in the Google Cloud console, reusing them if they already exist, and set the encrypted
secret as described in [Google sign-in and Gmail registration](registration.md#configure-google-once).
This is a manual step separate from the persistent OpenTofu bootstrap: the Google provider does
not support this standard web-client/consent setup. The app and client have persistent lifetime
and survive environment teardown; only environment copies of their settings are recreated by `up`.

## 4. Configure the GitHub repository

In the repository open **Settings → Secrets and variables → Actions** and add:

| Name | Kind | Value |
|------|------|-------|
| `GCP_PROJECT_ID` | variable | `<project-id>` |
| `GCP_PROJECT_NUMBER` | variable | the number printed in step 1 |
| `CLOUDFLARE_ZONE_ID` | variable | the Zone ID from step 2 |
| `PAGES_ENABLED` | variable | `true` — the `Docs` workflow publishes the documentation site only when it is set |
| `PAT` | secret | the token from step 3 |
| `CLOUDFLARE_API_TOKEN` | secret | the token from step 2 |
| `GOOGLE_OAUTH_CLIENT_ID` | secret | Google web OAuth client ID from the registration setup |
| `GOOGLE_OAUTH_CLIENT_SECRET` | secret | Client secret encrypted with the application AES key |
| `AES_KEY` | secret | a new key from `secret-helper generate-key` ([keys and users](keys-and-users.md#1-generate-the-key)) |

The `Snapshot` and `Release` workflows need nothing else — they publish images with the
workflow's own `GITHUB_TOKEN`.

In **Settings → Advanced Security** enable **Dependabot security updates**, so that Dependabot
opens pull requests fixing reported vulnerable dependencies. Weekly version updates need no
setting; they are configured in `.github/dependabot.yml`.

In **Settings → Pages** set **Source** to **GitHub Actions** (or run
`gh api -X POST repos/<owner>/<repository>/pages -f build_type=workflow`). From then on every push
to `master` publishes the documentation site at `https://<owner>.github.io/<repository>/`; set
`site_url` in `zensical.toml` to that address. GitHub Pages for a private repository needs a paid
GitHub plan.

Protect `master` with the ruleset committed in `.github/rulesets/master.json`:

```shell
gh api -X POST repos/<owner>/<repository>/rulesets --input .github/rulesets/master.json
```

Ordinary changes to `master` require a pull request whose checks passed. Contributors and
administrators cannot push directly, force-push or delete it. A pull request needs no approving
review, so a single maintainer can merge their own. Release automation has the dedicated App
exception configured below. Rulesets for a private repository need a paid GitHub plan.

Next: [create the environment](environment.md).

## Release automation

The Release workflow creates a release commit and a subsequent commit restoring development.
It requires a dedicated GitHub App to push those commits and their release tag atomically.
Ordinary contributors, administrators and workflow tokens keep the pull-request requirement.

1. Create a GitHub App in your account or organization settings. Disable webhooks if unused,
   grant repository **Contents: Read and write**, and install it only on this repository.
2. Generate a private key. Add its PEM contents as the repository secret
   `RELEASE_APP_PRIVATE_KEY`, and add the App's client ID as repository variable
   `RELEASE_APP_CLIENT_ID`. Keep the numeric App ID for the ruleset step.
3. Add only that App as an **Always allow** bypass actor on the existing master ruleset.
   The checked-in ruleset has no bypass by default. You can apply the exception with:

```shell
release_app_id=<numeric-app-id>
release_ruleset_id=$(gh api repos/<owner>/<repository>/rulesets \
  --jq '.[] | select(.name == "master") | .id')
jq --argjson app_id "$release_app_id" \
  '.bypass_actors = [{"actor_id": $app_id, "actor_type": "Integration", "bypass_mode": "always"}]' \
  .github/rulesets/master.json > /tmp/invoice-watcher-release-ruleset.json
gh api --method PUT repos/<owner>/<repository>/rulesets/"$release_ruleset_id" \
  --input /tmp/invoice-watcher-release-ruleset.json
```

The token action restricts installation access to this repository and requests contents write
permission. The App credentials are used for the atomic Git push; the normal workflow token
publishes the image and GitHub release. Repository configuration is manual; changing repository
files alone does not install the App or modify the live ruleset. Reapplying the original ruleset
without the exception disables release pushes.

Run **Release** from master with a major, minor or patch increment. Its release commit contains
the release's ArgoCD target revision, image tag and pod version label. A later commit restores
master and snapshot before artifacts are published. Builds still run tests and static analysis.
The release/restoration commits use the App's explicit bypass rather than pull-request checks.

If publication fails after the Git push, rerun that same failed workflow. It reuses the tag and
publishes from its original release commit; it never moves the tag. A fresh dispatch makes a new
release. If another change reached master before preparation, the workflow fails without changing
remote refs; start a new run from current master. Choose completed GitHub releases for deployment.
