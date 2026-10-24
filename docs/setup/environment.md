# Environment

The dev environment exists only while it is needed: create it, use it, destroy it. Both are done
by one manual GitHub Actions workflow.

The `Environment` workflow takes an action, `up` or `down`, and an environment (`dev`). `up`
creates the cloud resources and then installs the cluster services; running it again on an
environment that is already up only applies the differences. `down` destroys the cloud
resources, and the cluster with everything in it goes with them.

## Create

1. In the repository open **Actions → Environment → Run workflow**.
2. Choose `up` and `dev`, run it.

The workflow has two jobs:

| Job | What it does |
|-----|--------------|
| `infrastructure` | Checks that the `AES_KEY`, `PAT` and `CLOUDFLARE_API_TOKEN` secrets are set and stops before touching anything when one is missing. Then OpenTofu plans `deployments/infrastructure/environments/dev/ephemereal`, prints the plan in the log and applies exactly that plan: Secret Manager secrets, Firestore database, GKE cluster and node pool, global IP, Cloudflare DNS record. State is kept in the `<project-id>-tfstate` bucket. |
| `k8s-install` | Waits for the cluster, then `helmfile sync` installs ArgoCD, cert-manager, origin-ca-issuer, external-secrets and the `cluster-init` chart (gateway, certificate, secret sync, the ArgoCD application). |

From then on ArgoCD deploys the applications from `master` by itself.

## Check

Open `https://argocd.<domain>`. The user is `admin`; the initial password is generated in the
cluster:

```shell
gcloud container clusters get-credentials invoice-watcher-cluster \
  --zone europe-west1-b --project <project-id>
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d
```

The application `invoice-watcher-dev` should be *Synced* and *Healthy*, with the `kafka` and
`mail-producer` deployments in the namespace `invoice-watcher-dev`:

```shell
kubectl -n invoice-watcher-dev get pods
kubectl -n invoice-watcher-dev logs deploy/mail-producer -f
```

The certificate takes a minute or two after the first sync; until then the ArgoCD page does not
load.

## Deploy a new version

Nothing to do: every merge to `master` publishes the `snapshot` image, and ArgoCD applies changed
manifests. The pod pulls the image only when it starts, so to run the latest `snapshot` restart
it:

```shell
kubectl -n invoice-watcher-dev rollout restart deploy/mail-producer
```

## Destroy

Run **Environment** with `down` and `dev`. OpenTofu prints a destroy plan in the log and
applies exactly that plan: everything `up` created is destroyed, and the cluster goes with all it
contains.

!!! danger "Data is deleted"
    `down` deletes the Firestore database — users, mailboxes, folders and scan timestamps. There
    are no backups.

What stays: everything created by the [one-time setup](one-time.md) (state bucket, GitHub
Actions service account, enabled APIs) and the APIs enabled by `up`, which cost nothing.

## Cost

While the environment is up you pay for: one preemptible `e2-standard-2` node, the GKE cluster
management fee (one zonal cluster per billing account is covered by the GKE free tier), the
global IP address and the HTTPS load balancer of the gateway, and Firestore and Secret Manager
usage (within the free tier at this scale). After `down` only the state bucket remains.
