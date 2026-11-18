# Local development

No cloud account is needed: locally the mail producer stores users in an in-memory H2 database,
publishes to a Kafka container and scans a real mailbox of your choice.

## Build and verify

```shell
./gradlew build analyze   # compile, test, Checkstyle, SpotBugs
scripts/verify.sh         # the above + documentation checks and regenerated reference
```

`scripts/verify.sh` is what every pull request must pass. It regenerates the pages in
[Reference](../reference/index.md); commit them with the change.

## Local configuration in worktrees

Keep backend local configuration in the sibling `master` checkout. When you create a worktree
with `scripts/worktree.sh new <type>/<slug>`, existing ignored `application-local.yml` files are
linked from master into matching `apps/*/src/main/resources` directories. This also applies to
Claude Code's worktree-creation hook; Codex and OpenCode use the explicit script.

Prepare the producer and registration backend files in master using the setup instructions
below. Future backend applications participate automatically when their master resource directory
contains an ignored `application-local.yml` and the new checkout has the matching directory.
The script skips missing files or an absent master checkout, and preserves any existing destination,
including symbolic links. It does not generate configuration or credentials.

Edits through a link change the shared file used by other linked worktrees. Removing a worktree
leaves the files in master intact. Existing worktrees can apply the same linking rules with
`bash scripts/worktree.sh link-config`, or as part of the setup command below. Existing files
and links remain untouched; preserve worktree-specific settings before replacing them.
Keep master in place while using its shared configuration. Configuration paths and diagnostics
appear on stderr; successful creation still prints only the new checkout path on stdout.
If linking fails, creation returns an error and retains the checkout for inspection.

## Orca workspaces

Use JDK 25, Python 3 with venv support (or virtualenv), Git and Docker with Compose accessible
to your user.
From a checkout, run:

```shell
bash scripts/dev-setup.sh --check   # check prerequisites only
bash scripts/dev-setup.sh          # also link local config and install frontend dependencies
bash scripts/dev-setup.sh --warm-up # additionally compile Java main/test classes
```

The script always targets its own checkout, even when invoked by absolute path from another
working directory. It checks Java and Docker access and probes Python venv creation in a temporary
directory. It reuses Gradle's pinned Node/pnpm toolchain and frozen dependency installation.
Repeated runs preserve existing local configuration. Missing configuration files are reported
with their source paths; prepare them in master using the instructions on this page. Credentials
are unnecessary for compiling the project. Setup does not start applications, Kafka or H2, initialize
a database, or scan mail. A successful setup is not verification: run the build commands above.

In Orca's repository settings, select `origin/master` as the default base and show external
worktrees. Fetch before starting new work and use the repository commands for creation/removal:

```shell
git fetch origin
scripts/worktree.sh new chore/my-change
scripts/worktree.sh list
# After work has been reviewed, pushed and merged, remove it through the safety checks:
scripts/worktree.sh rm my-change
```

Orca displays the resulting external checkout. Open that checkout for editing and run setup
there. The shared script enforces branch naming and the sibling worktree layout. Native Orca
removal and automatic cleanup do not run the repository's lifecycle checks; disable automatic
cleanup for this project and use the shared removal command. Do not use an archive hook for cleanup.

The committed `orca.yaml` declares `bash scripts/dev-setup.sh` as the setup command and
`wait-for-setup` as the agent startup policy. Both files reach new checkouts together when the
change is merged into their base branch. Existing external worktrees still run setup explicitly.
Review and trust the shared setup command when Orca asks. If repository settings select local-only
scripts, select shared scripts to use this file; clear an old local setup command to avoid running
setup twice. Base ref, external-worktree visibility and cleanup rules remain Orca settings,
not fields in this setup file. Keep the local archive command empty. No default application tabs
or shared build directories are configured.

### Workflow commands

Use the hosted backend's existing project commands or generated skills in Orca. There are no
separate Orca workflow definitions. For example, Codex uses `$iw-lightweight` with the workflow
inputs, while Claude Code and OpenCode use their existing slash commands. Native chat discovery
uses the backend's reported command/skill catalogue; make sure the session is attached to the
intended project checkout. Orca's built-in Plan mode is a conversation control and is distinct
from the repository's planning workflow.

### Parallel workers and cleanup

The repository launcher selects the execution backend and session host separately. In an
Orca session it starts the worker visibly in Orca, using the Chat/CLI preference configured for
that backend. Outside Orca it uses the backend's native interface. Choose explicitly with
`--host orca` or `--host backend` when needed:

```shell
scripts/worktree.sh new chore/my-worker
python3 scripts/worker.py start --host orca --backend codex --default-agent --name my-worker --worktree "$(cd ../my-worker && pwd)" --command '/plan describe the intended change'
python3 scripts/worker.py status my-worker
python3 scripts/worker.py resume my-worker
```

The worktree is protected before launch. The worker remains open for discussion and approval;
resume continues its conversation without granting approval. Orca can create a new assignment
within that conversation, so the launcher records updated assignment identities. The worker
reports its current Orca assignment when pausing and retains its repository approval phase.

Launch resolves the assigned checkout within the caller's Orca project registration, so a
duplicate registration for the same path cannot capture the worker. Hidden projects and archived
workspaces are rejected before launch and checked again before continuation. If the caller's
project cannot be resolved, inspect `orca worktree ps --json` and `orca worktree show`, then pass
the intended visible workspace as `--orca-workspace '<exact-selector>'`. The launcher does not
create, unhide or delete project registrations to resolve ambiguity. Local workers and default backend
agents are supported. Named-agent selection, explicit sandbox overrides, Codex source sockets
or parent-task forks, explicit OpenCode servers and OpenCode model variants are rejected rather
than dropped. Orca uses its configured backend permissions. Supported model selection uses
`--model`; no separate Orca command definitions or worker execution backend are introduced.

Repository status remains the authority for pruning. A completed Orca assignment or closed tab
does not mark repository work complete. After merge, `scripts/worktree.sh rm my-worker` or the
normal prune command checks repository lifecycle, PR and Git safety, requests release of the
exact recorded worker, then removes its Orca workspace and branch. Unrelated tabs, retained or
unconfirmed resources block removal with a diagnostic; cleanup does not force removal or run
archive hooks. Partial cleanup retains checkpoints for a safe retry. Saved Orca history remains.

Failed or ambiguous launch/continuation receipts are retained under `agent-workers/orca-receipts`
in the common Git directory. `python3 scripts/worker.py recover my-worker` replays an unresolved
operation only if Orca returned its mutation request ID. Without it, inspect the host and recorded
assignment before any recovery; do not start another worker. Existing backend-native workers
continue to use their recorded native sessions and are not migrated automatically.

### Shared services and parallel work

Keep one running registration/producer stack by default, with parallel editing and builds in
other checkouts. Start Kafka from one designated checkout using the command below. Its host
port is 9092; its controller listener stays inside Docker, leaving host port 9093 for H2.
Run the H2 server and initialize its empty disposable database from one designated checkout.
That checkout owns `libs/dao/user-configuration/jpa-impl/build/local-h2` (the module's build
output); do not clean or remove it while using its data. Other checkouts connect to the same
H2 server. Kafka's container lifecycle belongs to its designated checkout too: do not run a
second Compose project against the same host port.

Build outputs, project `.gradle`, frontend `node_modules` and `.venv-docs` stay private to each
checkout. Gradle manages its user-level dependency cache and locking; do not link build or
installation directories between checkouts.

For an explicitly separate runtime stack, allocate its own ports and separate mailbox/user
configuration before starting another producer. Sharing credentials and a database does not
coordinate scans: two producers can scan the same mailbox concurrently. Preserve the shared
configuration sources; use dedicated ignored local configuration in the second checkout.
Separate scans need distinct mailboxes and persistence, with matching backend/producer DAO and
AES settings within that stack. HTTP port overrides alone do not isolate scanning.

For example, after preparing that independent configuration, start its backend and producer
in separate terminals:

```shell
./gradlew :apps:user-registration-backend-app:bootRun --args='--spring.profiles.active=local --server.port=8180 --invoice-watcher.user-registration.public-base-url=http://localhost:4300 --invoice-watcher.user-registration.secure-cookies=false'
./gradlew :apps:mail-producer-app:bootRun --args='--spring.profiles.active=local --server.port=8181'
```

Prepare a private frontend proxy file targeting the second backend:

```shell
mkdir -p frontend/build
python3 - <<'PYPROXY'
import json
from pathlib import Path
proxy = json.loads(Path("frontend/proxy.json").read_text())
for route in proxy.values():
    route["target"] = "http://127.0.0.1:8180"
Path("frontend/build/proxy-secondary.json").write_text(json.dumps(proxy, indent=2) + "\n")
PYPROXY
./gradlew :apps:user-registration-frontend-app:frontendServe -PfrontendPort=4300 -PfrontendProxyConfig=build/proxy-secondary.json
```

These Gradle properties override only the development server; their defaults are port 4200
and `proxy.json`. Following [Google client setup](registration.md#configure-google-once), register
both callbacks on the second stack's public frontend origin:

```text
http://localhost:4300/login/oauth2/code/google
http://localhost:4300/login/oauth2/code/gmail
```

The frontend proxy forwards both callbacks to the backend on port 8180.
Use the same hostname consistently, and avoid logging into both stacks in the same browser
profile: cookies are shared across ports on the same host. Setup never changes OAuth settings.
For another docs preview, use `.venv-docs/bin/zensical serve --dev-addr 127.0.0.1:8001` after
verification has prepared its virtualenv.

## Run the mail producer

1. Start Kafka:

    ```shell
    docker compose -f .run/files/kafka.yml up -d
    ```

2. [Generate a key and encrypt the mailbox password](keys-and-users.md).
3. Create `apps/mail-producer-app/src/main/resources/application-local.yml` (git-ignored). Every
   property is described in the [configuration reference](../reference/configuration.md); a
   working local setup looks like this:

    ```yaml
    invoice-watcher:
      secret:
        aes:
          key: <key>
      event-producer:
        mode: kafka
        kafka:
          bootstrap-servers: localhost:9092
      user-configuration:
        dao:
          mode: jpa
          jpa:
            data-source:
              basic:
                url: jdbc:h2:mem:invoice-watcher
                username: sa
                password: ""
                driver: org.h2.Driver
            ddl-auto: create
            dialect: org.hibernate.dialect.H2Dialect
      mail-producer:
        scheduler:
          rate: 1m
        task:
          topic: all-mails
        mock:
          user:
            enabled: true
            email: <your e-mail address>
            host: <IMAP host, e.g. imap.gmail.com>
            password: <encrypted mailbox password>
            folders:
              - INBOX
    ```

   To use a Cloud SQL for PostgreSQL instance instead of H2, replace `data-source.basic` with
   `data-source.gcp` (instance connection name and database name). The application then signs in
   with IAM database authentication as the service account of its Google default credentials (on
   GKE, the workload's service account), without a username or password. Adding that service
   account as an IAM database user and granting it `roles/cloudsql.client` and
   `roles/cloudsql.instanceUser` is up to you.

4. Run the `local` configuration in IntelliJ (it starts Kafka and `MailProducerApp` with the
   `local` profile), or from the command line:

    ```shell
    ./gradlew :apps:mail-producer-app:bootRun --args=--spring.profiles.active=local
    ```

5. Watch the events — see [In action](../results/index.md#watch-the-topic).

To try Firestore instead of H2, run the Datastore emulator and set
`invoice-watcher.user-configuration.dao.mode: firestore` with
[`dao.firestore.host`](../reference/configuration.md#invoice-watcher-user-configuration-dao-firestore)
pointing at it.

## Run registration and the producer together

Gradle downloads and uses the pinned Node LTS, pnpm, frontend packages and Playwright Docker image. Global Node
or pnpm installations are not required. Backend integration tests use Testcontainers to start a
pinned Datastore emulator container; a running Docker daemon is required, and a local Google
Cloud CLI installation is unnecessary. Full builds run browser tests and their Angular test servers
in a pinned Playwright container, including its browser system libraries. No host-level sudo or
OS-library installation is needed; Docker must be accessible to your user.

For shared H2, start the loopback database server in one terminal:

```shell
./gradlew :libs:dao-user-configuration-jpa-impl:localH2Server
```

Initialize its **empty disposable** database once in another terminal:

```shell
./gradlew :libs:dao-user-configuration-jpa-impl:localH2Initialize
```

Initialization refuses existing tables. The local database lives in the JPA module's ignored
build directory; cleaning/removing it loses registrations and scan progress. This is development
initialization, not a schema upgrade tool. Durable SQL schema migrations are not provided yet.

Configure both Java applications in their ignored `application-local.yml` with the same
`invoice-watcher.user-configuration.dao` settings: mode `jpa`, URL
`jdbc:h2:tcp://localhost:9093/./invoice-watcher`, user `sa`, empty password, driver `org.h2.Driver`,
dialect `org.hibernate.dialect.H2Dialect` and **ddl-auto `validate`**. Two independent in-memory
H2 databases cannot share registrations. Disable the producer mock user. Both apps also need the
same AES key and `invoice-watcher.oauth.google.client-id` / `client-secret`; the latter is encrypted
with that key. Keep every secret value in ignored files or environment variables.

When updating an existing local setup, move the former `invoice-watcher.google-oauth` settings
to `invoice-watcher.oauth.google`. The environment variables are now
`INVOICE_WATCHER_OAUTH_GOOGLE_CLIENT_ID` and `INVOICE_WATCHER_OAUTH_GOOGLE_CLIENT_SECRET`.
The old application prefix is no longer read. Keep the same client ID, encrypted secret and AES
key; this configuration rename does not require creating another Google client.

In the backend's local file set `invoice-watcher.user-registration.public-base-url` to
`http://localhost:4200` and `invoice-watcher.user-registration.secure-cookies` to `false` for local
HTTP only. Follow [Google client setup](registration.md#configure-google-once), including both
local callback URLs. Start the backend:

```shell
./gradlew :apps:user-registration-backend-app:bootRun --args=--spring.profiles.active=local
```

The producer and backend otherwise both use port 8080; set `server.port: 8081` on the producer when
running both locally. Start Kafka and the producer as above, then the Angular development server:

```shell
./gradlew :apps:user-registration-frontend-app:frontendServe
```

Open `http://localhost:4200`. Its `/api`, `/oauth2` and `/login/oauth2` proxy routes reach the backend;
frontend routes stay on Angular. Use the same hostname consistently for cookies and callbacks.
For Firestore instead, point both apps at the same emulator/project using the shared DAO settings.
The test suite starts and cleans up its own isolated Datastore emulator container, using a mapped
port and requiring no Google credentials.

The shared UI library and local catalogue are described in the frontend README. `./gradlew build`
includes their compilation and browser checks. Toolchain and container-image downloads are automatic.
CI uses the same Gradle build and browser container. Review visual baseline changes before accepting them.

## Run registration in IntelliJ IDEA

Reload the Gradle project, use Java 25 for the project and Gradle JVM, and select the shared
**registration** compound configuration from the Run/Debug menu. It starts
**UserRegistrationBackendApp** with the `local` profile on port 8080 and **registration-frontend**
using Gradle's managed Node/pnpm toolchain. Open `http://localhost:4200` after both have started.
Use Debug for backend breakpoints; frontend development continues through the Angular server.
The backend runner sets the local public URL and disables secure cookies for local HTTP.

First supply the backend's ignored `application-local.yml` with the DAO, AES and Google client
settings described above and configure the Google localhost callback URLs. The shared runner
files contain no credentials. For the shared H2 setup, start **registration-h2** separately and
run **registration-h2-initialize** once against its empty disposable database. Keep the server
running before starting the compound; initialization refuses existing tables, so it is not a
before-launch step. The compound starts its children concurrently and does not start or reset a
database. For another persistence setup, prepare that database instead.

The frontend runner compiles the shared UI before serving Angular and does not install browser-test
OS packages. Kafka and the producer are unnecessary for managing registration; start them
separately when testing actual scanning, with the producer on port 8081 as described above.

## Documentation site

`scripts/verify.sh` installs the pinned site generator (Zensical) into `.venv-docs/` in the
working tree and builds the site; it needs `python3` with `venv` (`python3-venv` on Debian/Ubuntu)
or `virtualenv`. The `Docs` workflow publishes the site from `master` on every push. After one
run, preview the site with live reload:

```shell
scripts/verify.sh
.venv-docs/bin/zensical serve
```

In IntelliJ, the `docs` run configuration starts the same preview at http://localhost:8000/invoice-watcher/.

## How we work

Changes are spec-driven: behavior is described in specifications in the repository first, then
implemented and tested against numbered clauses. Every change goes through a pull request to
`master`. The rules for AI agents — a good summary for humans too — are in
[`AGENTS.md`](https://github.com/Guslarz/invoice-watcher/blob/master/AGENTS.md).

## Processor recovery tests

The [processor preparation guide](event-processing.md) explains how to exercise synthetic
processing, quarantine and replay against isolated Testcontainers Kafka/Datastore services.
There is no standalone replay helper or recognition application to start yet.
