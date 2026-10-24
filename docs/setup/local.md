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
      mail-producer:
        scheduler:
          rate: 1m
        task:
          topic: all-mails
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
`invoice-watcher.mail-producer.dao.mode: firestore` with
[`dao.firestore.host`](../reference/configuration.md#invoice-watcher-mail-producer-dao-firestore)
pointing at it.

## Documentation site

`scripts/verify.sh` installs the pinned site generator (Zensical) into `.venv-docs/` in the
working tree and builds the site; it needs `python3` with `venv` (`python3-venv` on Debian/Ubuntu)
or `virtualenv`. After one run, preview the site with live reload:

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
