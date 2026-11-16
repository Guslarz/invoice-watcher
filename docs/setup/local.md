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
same AES key and `invoice-watcher.google-oauth.client-id` / `client-secret`; the latter is encrypted
with that key. Keep every secret value in ignored files or environment variables.

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
