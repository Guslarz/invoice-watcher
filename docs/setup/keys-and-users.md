# Keys and users

InvoiceWatcher logs in to mailboxes with their passwords, so it has to store them. They are
stored encrypted with an AES key that only the running application has. Every encryption uses
AES-256-GCM with a fresh random IV, so the same password encrypted twice gives two different
values, and a value encrypted with one key cannot be decrypted with another. Passwords are
decrypted only for the moment of connecting to a mailbox and are never logged.

## The `secret-helper` tool

Build it once:

```shell
./gradlew :apps:secret-helper-app:bootJar
```

and run it with `java -jar apps/secret-helper-app/build/libs/secret-helper.jar <command>` (or use
the `SecretHelperApp - …` run configurations in IntelliJ). It asks for every value on standard
input, so keys and passwords do not end up in the shell history.

| Command | What it does |
|---------|--------------|
| `generate-key` | Asks for a password and prints a new key derived from it. |
| `encrypt` | Asks for the key and a value, prints the encrypted value. |
| `decrypt` | Asks for the key and an encrypted value, prints the original value. |

All options are listed in the [secret-helper reference](../reference/cli.md).

## 1. Generate the key

```shell
$ java -jar apps/secret-helper-app/build/libs/secret-helper.jar generate-key
Enter encryption password: <password>
<Base64 key, 44 characters>
```

Keep the printed key (e.g. in a password manager). The password cannot recreate it — the salt is
random and not shown.

The application reads the key from
[`invoice-watcher.secret.aes.key`](../reference/configuration.md#invoice-watcher-secret-aes)
(environment variable `INVOICE_WATCHER_SECRET_AES_KEY`) and does not start without it.

Where the key goes:

- **Dev environment** — the `AES_KEY` GitHub secret ([one-time setup](one-time.md#4-configure-the-github-repository)):

    The workflow stores it in the Secret Manager secret `aes_key`; external-secrets syncs it into
    the `invoice-watcher-dev` namespace and the mail producer gets it as an environment
    variable.

    To change the key, update the secret, run `Environment` with `up` again and restart the
    `mail-producer` pod once external-secrets has refreshed its secret (at most an hour).
    Passwords encrypted with the old key must be encrypted again.

- **Locally** — the git-ignored `application-local.yml` ([local development](local.md)).

Never put the key into a committed file.

## 2. Encrypt a mailbox password

```shell
$ java -jar apps/secret-helper-app/build/libs/secret-helper.jar encrypt
Enter encryption key: <the key from step 1>
Enter input for encryption: <the mailbox password>
<encrypted value>
```

Use the password the mail server accepts for IMAP. Many providers do not accept the account
password for IMAP once two-factor authentication is on and require an *app password* instead
(e.g. Gmail, Outlook.com); IMAP access may also have to be enabled in the mailbox settings.

To check a value, `decrypt` prints the original password back.

## 3. Add a user

A user is an e-mail address with one or more mailboxes, each with folders to watch — see
[how it works](../system/index.md#what-invoicewatcher-needs-to-know).

There is no user interface or API for managing users yet. Today a user can be defined in the
application configuration as the **mock user**:

when [`invoice-watcher.mail-producer.mock.user.enabled`](../reference/configuration.md#invoice-watcher-mail-producer-mock-user)
is `true`, the application creates the user at startup, or replaces it if it exists, from the
`...mock.user.*` properties: the e-mail address (also used as the mailbox username), host, port
(default 993), encrypted password and a comma-separated list of folders. Every start resets the
user's folders: their start date is set to ten days before the start and their scan position is
cleared.

For the dev environment that means adding to
`deployments/k8s-apps/apps/mail-producer/overlays/dev/config/.env` (and merging it to `master`):

```properties
INVOICE_WATCHER_MAIL_PRODUCER_MOCK_USER_ENABLED=true
INVOICE_WATCHER_MAIL_PRODUCER_MOCK_USER_EMAIL=jan@example.com
INVOICE_WATCHER_MAIL_PRODUCER_MOCK_USER_HOST=imap.example.com
INVOICE_WATCHER_MAIL_PRODUCER_MOCK_USER_PORT=993
INVOICE_WATCHER_MAIL_PRODUCER_MOCK_USER_PASSWORD=<encrypted value from step 2>
INVOICE_WATCHER_MAIL_PRODUCER_MOCK_USER_FOLDERS=INBOX,Invoices
```

The encrypted password may be committed — it is useless without the key. The mock user is one
user with one mailbox, and every restart scans its last ten days again; it is meant for trying
the system out, not for real users.
