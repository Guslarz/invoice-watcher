# Google sign-in and Gmail registration

Open `https://register.<configured-domain>` and sign in with Google. Connect Gmail, approve its
mail permission, choose watched IMAP folders and save your registration. Only the signed-in
Google account can be connected. Registration does not accept mailbox passwords or custom servers;
manually provisioned password-based mailboxes remain available to the mail producer separately.

A folder's start date is optional when you add it. Without one, its first scan reads all available
history. After saving, the date is read-only. Changing the watched folder name creates a fresh scan
position for that folder; reconnecting the same account preserves progress. Changes apply to the
next scheduled scan. You can configure up to 16 uniquely named folders, each at most 255 characters;
API requests are limited to 32 KiB.

When Gmail authorization expires or is revoked, sign in again. Sign-in automatically continues to
Google's consent page when reconnection is needed, without a separate reconnection screen. Refusing
mail access keeps you signed in and leaves your configuration intact. Signing out alone does not
stop scheduled scans. Backend restarts end browser sessions, but retain registrations and grants.

Disconnect Gmail to remove the mailbox and its progress, or delete your InvoiceWatcher registration
to remove all your registration data. Both actions ask for confirmation. They leave your Google
account and already published events untouched. Removal attempts to revoke Google access; if Google
cannot be reached, local deletion still completes and the interface explains how to remove access
in your Google Account permissions. Revocation can also affect sign-in authorization for the same
OAuth client. An already running scan can finish work it loaded before removal.

Development data is temporary: environment `down` deletes registrations, grants and scan progress.
It does not revoke Google-side permissions. Recreating the environment does not restore the data.

## Configure Google once

1. In the Google Cloud console, open **Google Auth Platform** for your project. Configure Branding
   with the application name, support email and developer contact. Supply the public application
   URLs and authorized domain where required.
2. Set Audience to **External**, keep publishing status **Testing**, and explicitly add your test
   Google accounts. Configure Data Access for `openid`, email/profile identity scopes and
   `https://mail.google.com/`. Gmail IMAP requires this broad mail scope, although InvoiceWatcher
   opens folders read-only and never changes messages.
3. Create an OAuth client of type **Web application**. Add these exact authorized redirect URIs,
   substituting your configured domain:

    ```text
    https://register.<configured-domain>/login/oauth2/code/google
    https://register.<configured-domain>/login/oauth2/code/gmail
    ```

   For local development, add both equivalents using `http://localhost:4200`. The backend handles
   these callbacks through the Angular development proxy. Sign-in requests only identity scopes;
   connecting Gmail separately requests offline mail access. No browser client secret is needed.
4. Copy the client ID. Encrypt the client secret using the same AES key as the applications with
   [secret-helper](keys-and-users.md#the-secret-helper-tool). Supply the secret interactively to
   `encrypt`, rather than placing it in command-line arguments or shell history.
5. Set GitHub Actions secrets `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET` (the
   **encrypted** client secret), in addition to `AES_KEY`. Environment `up` stores the client
   settings in Secret Manager, syncs them into Kubernetes and injects them into the backend and
   producer. Neither the frontend nor its image receives these values. Re-run `up` and restart
   both Java workloads after changing client settings. Changing the AES key requires re-encrypting
   the client secret and existing stored grants; automatic key rotation is not provided.

Google client configuration is created manually and survives environment `down`; the environment's
Secret Manager copies and database do not. The source GitHub secrets also survive. After `up`, use
the same Google client and register again. Do not put secret values in installation configuration.

In External / Testing mode, Gmail authorization and refresh tokens expire after seven days; sign-in
then continues through consent again. This expiry is not the intended public-production experience.
Before opening the app publicly, address Google's verification and applicable restricted-scope
security assessment requirements, including any applicable exceptions. See Google's documentation
on [testing audiences](https://support.google.com/cloud/answer/15549945) and
[restricted scopes](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification).

## Troubleshooting

- **Access blocked during consent:** verify the exact test account is listed and redirect URIs
  match the public origin and client registration path.
- **Account unavailable:** an existing manually provisioned user may already use that email. The
  app does not claim existing data automatically. An operator must resolve the collision explicitly.
  Changes to an existing Google identity's email likewise require manual resolution.
- **Configuration changed:** reload before editing again. Concurrent writes are rejected rather
  than overwriting a newer version.
- **Provider temporarily unavailable:** retry later. A temporary refresh failure does not discard
  grants or scan progress.
- **Permission denied:** use the normal sign-in/connection flow; no direct database edits are needed.
