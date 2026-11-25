# Environment

The dev environment exists only while it is needed: create it, use it, destroy it. Both are done
by one manual GitHub Actions workflow.

The `Environment` workflow takes an action, `up` or `down`, and an environment (`dev`). `up`
creates the cloud resources and then installs the cluster services; running it again on an
environment that is already up only applies the differences. `down` destroys the cloud
resources, and the cluster with everything in it goes with them.

## Create

1. In the repository open **Actions → Environment → Run workflow**.
2. Choose the workflow ref: `master` for development, or a completed release's tag such as
   `v1.3.0` for a released deployment. Choose `up` and `dev`, then run it.

You can select a release tag explicitly with the GitHub CLI:

```shell
gh workflow run environment.yml --ref v1.3.0 -f action=up -f environment=dev
```

Both infrastructure and cluster bootstrap use that workflow run's checkout. There is no separate
version input. The local cluster-init chart is installed from that checkout. ArgoCD follows the
`targetRevision` committed in its Application definition: `master` in development commits and
`v1.3.0` in that release commit. Selecting another ref does not override the committed value.

The workflow has two jobs:

| Job | What it does |
|-----|--------------|
| `infrastructure` | Checks that the `AES_KEY`, `PAT` and `CLOUDFLARE_API_TOKEN` secrets are set and stops before touching anything when one is missing. Then OpenTofu plans `deployments/infrastructure/environments/dev/ephemeral`, prints the plan in the log and applies exactly that plan: Secret Manager secrets, Firestore database, GKE cluster and node pool (in the configured zone and under the configured name), Kafka's 10 GiB data disk `kafka-data` in the same zone, global IP, Cloudflare DNS record. State is kept in the `<project-id>-tfstate` bucket. |
| `k8s-install` | Waits for the cluster, then `helmfile sync` installs ArgoCD, cert-manager, origin-ca-issuer, external-secrets and the `cluster-init` chart (gateway, certificate, secret sync, the volume for Kafka's disk, the ArgoCD application). |

From then on ArgoCD continuously reconciles the applications from that committed target revision.

## Check

Open `https://argocd.<domain>`. The user is `admin`; the initial password is generated in the
cluster:

```shell
gcloud container clusters get-credentials <cluster-name> \
  --zone <zone> --project <project-id>
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d
```

The application `invoice-watcher-dev` should be *Synced* and *Healthy*, with the `kafka` stateful set
and the `mail-producer` deployment in the namespace `invoice-watcher-dev`:

```shell
kubectl -n invoice-watcher-dev get pods
kubectl -n invoice-watcher-dev logs deploy/mail-producer -f
```

The certificate takes a minute or two after the first sync; until then the ArgoCD page does not
load.

## Deploy a new version

For development, every merge to `master` publishes the `snapshot` image, and ArgoCD applies changed
manifests. The pod pulls the image only when it starts, so to run the latest `snapshot` restart
it:

```shell
kubectl -n invoice-watcher-dev rollout restart deploy/mail-producer
```

To deploy a release instead, run Environment with `up` from its completed release tag. That
commit contains both the pinned ArgoCD revision and the matching versioned application image.
A later merge or snapshot image publication does not upgrade that deployment. To switch back to
development, run `up` from `master`. Applying older definitions does not restore data or guarantee
that an infrastructure or application downgrade is compatible with existing state.

## Destroy

Run **Environment** with `down` and `dev` from the same release tag used for `up` (or `master`
for development). Both actions use the selected checkout's OpenTofu files:

```shell
gh workflow run environment.yml --ref v1.3.0 -f action=down -f environment=dev
```

OpenTofu prints a destroy plan in the log and
applies exactly that plan: everything `up` created is destroyed, and the cluster goes with all it
contains.

!!! danger "Data is deleted"
    `down` deletes the Firestore database — users, mailboxes, folders and scan positions — and
    Kafka's disk with all published events. There are no backups.

What stays: everything created by the [one-time setup](one-time.md) (state bucket, GitHub
Actions service account, enabled APIs) and the APIs enabled by `up`, which cost nothing.

## Cost

While the environment is up you pay for: one preemptible `e2-standard-2` node, the GKE cluster
management fee (one zonal cluster per billing account is covered by the GKE free tier), the
global IP address and the HTTPS load balancer of the gateway, Kafka's 10 GiB disk (about $1 a
month), and Firestore and Secret Manager usage (within the free tier at this scale). After `down` only the state bucket remains.

## Registration applications

After `up`, `https://register.<configured-domain>` serves the Angular frontend. The gateway routes
API and Google callback paths to the registration backend; the producer remains private. Frontend
and backend have independent images and health probes. Google OAuth settings and the shared AES
key are injected only into the Java workloads. Configure them before `up` using the
[registration setup](registration.md#configure-google-once).

`down` deletes registrations, encrypted grants and scan progress along with the database. It does
not remove the manually configured Google Auth Platform app or Web application OAuth client,
replace their credentials or revoke Google permissions. Both have persistent lifetime outside
ephemeral OpenTofu resources; only Secret Manager copies of the settings are destroyed and
recreated from GitHub secrets. The next
`up` starts without registered users; the producer mock user is disabled in this deployment.

## Attachment storage

The canonical installation values for mail/evidence retention live in
`deployments/installations/dev-mail-evidence.json`.
Installation generation renders the producer settings and topic initialization script from it;
OpenTofu reads its evidence lifetime for the bucket lifecycle. all-mails has explicit seven-day
retention, delete-only cleanup, no byte-retention cap and broker append timestamps. The development
broker default is also seven days, and automatic topic creation is disabled.
An idempotent initialization Job creates or reconciles the topic; the producer verifies effective
settings before publishing. Changing retention requires an attachment-window compatibility review.

After the [one-time capture-role setup](one-time.md#attachment-capture-role), environment up
provisions a dedicated regional GCS attachment bucket with uniform access and
public access prevention. Soft delete and versioning are disabled. The producer's separate
mail-producer-sa workload identity has create/get and bucket-policy inspection permission, without
object deletion or overwrite permission; registration's app-sa does not receive this bucket role.
Bootstrap metadata supplies the bucket name and capture database project through the existing
external-secrets path, without service-account credentials.

PDF bytes expire after their nine-day availability window; lifecycle deletion is asynchronous.
Complete environment down deletes the bucket contents and capture metadata along with the existing
Kafka and Firestore data. Nothing here is a persistent audit archive. Keep the current bucket's
retention policy fixed; any future retention change needs a new reviewed policy/bucket so existing
references are not deleted ahead of their recorded deadlines. See
[mail attachment evidence](mail-attachments.md) for configuration, duplicates and failure recovery.
