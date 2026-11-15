---
id: SPEC-REG
title: User registration
status: implemented
audience: [user, operator, developer]
modules:
  - apps/user-registration-backend-app
  - apps/user-registration-frontend-app
  - libs/user-registration/api
  - libs/user-registration/impl
---

# User registration

## Purpose

Users sign in with Google and connect Gmail for background scanning, manage watched folders,
and remove their registration without editing storage or handling mailbox credentials.

## Behavior

- **REG-001** A user signs in through Google, the only configured OpenID Connect provider, before accessing
  registration or stored mailbox configuration. The system accepts only a verified email address
  and binds ownership to the provider issuer and subject, not a client-supplied user identifier.

- **REG-002** The first registration creates a configuration for the signed-in user's verified
  email address and at least one mailbox with at least one folder. Repeating registration for the
  same owner does not replace existing configuration. An email address already owned by another
  identity is rejected without attaching that identity or modifying existing configuration.

- **REG-003** A signed-in user can read and edit only their own self-service Gmail OAuth
  configuration, connect and disconnect Gmail, and manage watched folders. Registration UI and
  API do not accept username/password credentials, custom IMAP servers or credential-type changes
  to basic authentication. Stored passwords and encrypted credential values are never returned.
  Existing manually provisioned basic-auth configurations remain outside self-service management.

- **REG-004** A self-service mailbox uses the fixed Gmail IMAPS endpoint and the Google-verified
  mailbox identity. When adding a folder, users supply a unique nonblank name and an optional
  ISO-8601 start date. The start date is read-only afterward.
  At most 16 folders are accepted, with names up to 255 characters and JSON requests up to 32 KiB.
  Invalid input changes no configuration. The registration backend does not establish IMAP
  connections or discover folders. Client-supplied server or password fields are rejected.

- **REG-005** Reauthorizing the same Gmail account preserves retained folders' identifiers
  and scan positions. Renaming or adding a folder creates a new identifier and empty position;
  its optional start date is selected as part of creating that new folder configuration. Omitting
  the start date requests scanning from the beginning. The interface displays existing start dates
  read-only, and the API rejects changes to a retained folder's start date without mutation.

- **REG-006** Configuration edits and deletion are atomic. An edit based on an outdated
  configuration version fails with a conflict instead of overwriting a newer edit. A concurrent
  scan-position update does not revert configuration edits, recreate removed folders or restore
  removed users. Unchanged folders keep their latest committed scan positions.

- **REG-007** Deleting registration removes its mailbox configuration, folders and ownership
  binding and locally held OAuth grants. Google grant revocation is attempted before local token
  removal; a provider failure does not block deletion or retain credentials for retries. The user
  is told if provider-side access may remain and how to revoke it manually. It does not delete the external identity or already published events. An in-flight
  scan may finish work already loaded, but subsequent scans do not load deleted configuration.

- **REG-008** The browser communicates with the registration backend over HTTPS on the same
  public origin. Authentication uses secure HttpOnly session cookies; state-changing requests
  require CSRF protection. Unauthenticated API calls return 401, forbidden operations return 403,
  missing resources return 404, invalid input returns 400, oversized bodies return 413 and conflicting writes return 409.
  Error responses and logs contain no mailbox passwords, encrypted values or authentication secrets.

- **REG-009** The frontend provides sign-in, initial registration, configuration editing,
  deletion confirmation and sign-out. It displays loading, empty, success and error states, labels
  every form input, associates field errors with inputs, supports keyboard navigation and visible
  focus, respects reduced motion and meets WCAG 2.2 AA contrast and reflow requirements.

- **REG-010** Development registration data is stored in the environment's Firestore database.
  Environment down deletes it; a later up does not restore registrations. The interface explains
  this before registration. The mock user is disabled in the self-service deployment.

- **REG-011** Google sign-in requests only identity scopes. Connecting Gmail is a separate,
  explicit server-side OAuth authorization-code flow requesting offline access and the
  https://mail.google.com/ scope. Refusing mailbox consent leaves sign-in and existing
  configuration intact. Both flows validate state; login validates its OpenID Connect nonce.

- **REG-012** Gmail connection callbacks are bound to the signed-in owner, intended mailbox
  and configuration version using single-use expiring state. The connected Google issuer and
  subject must match the signed-in identity; a different account is rejected without mutation.
  Authorization codes are exchanged only in the backend. A grant is saved only after required
  scopes and mailbox identity are verified and a usable refresh token is available.

- **REG-013** After Google sign-in, the backend checks any existing Gmail connection. If
  reconnection is required, it automatically continues into Gmail authorization and returns to the
  application after consent; there is no separate reconnection screen. Google may require user
  consent and the application cannot renew an expired or revoked refresh grant silently. Denying
  consent leaves the user signed in with a visible connection-status explanation. Refresh-token
  replacement for the same grant is atomic and preserves scan
  positions. If Google omits a refresh token, an existing token is retained only for the same
  owner, account and grant; a new connection without a usable token is not marked connected.

- **REG-014** Disconnecting Gmail removes the local grant and mailbox from future scans and
  attempts provider revocation as in REG-007. Sign-out ends the application session without
  disconnecting Gmail. Reconnecting an existing mailbox preserves positions; removing and later
  adding a mailbox creates new folder identities.

- **REG-015** Registration stores configuration in the selected Firestore or JPA database.
  Both modes support the same self-service ownership, Gmail OAuth credentials, atomic edits,
  conflicts and scan-position preservation. Registration and the producer use the same database.
  Missing or invalid storage-mode configuration fails startup naming the invalid property.

## Acceptance

- **REG-001** Given no authenticated session, when configuration is requested, then the API returns
  401; given an unverified email or a changed email for an established owner, access fails without
  silently changing ownership or the user key.

- **REG-002** Given a verified new identity and valid mailbox, when registration succeeds, then
  exactly one configuration is created; retrying or colliding with another owner changes nothing.

- **REG-003** Given users A and B, when A submits B's identifiers, then no B data is read or changed;
  when a password, basic-auth credential type or custom server is submitted, the API rejects it
  without mutation. Manually provisioned basic-auth data is not editable through self-service.

- **REG-004** Given a blank folder name, invalid start date or duplicate folder, when a write is submitted,
  then it returns 400 and the entire previous configuration remains intact.

- **REG-005** Given retained folder positions, when the same Gmail account is reauthorized,
  then those positions survive. Given an existing folder, when its start date is displayed, then
  it is read-only; an API attempt to change it returns 400 without mutation or progress reset.
  Given a renamed or newly added folder, when saved, then it has a fresh identifier and empty
  position, uses the start date selected at creation (all history if omitted), and unrelated
  folders remain unchanged.

- **REG-006** Given two editors and a running scan, when an editor saves and the scan advances,
  then both configuration and progress survive; a stale editor gets 409. Given a removed folder,
  a delayed position update cannot recreate it or alter its replacement.

- **REG-007** Given a registered user and an in-flight scan, when registration is deleted, then
  configuration and ownership are removed atomically, delayed writes do not recreate them, and
  the next scan excludes the user; already published events remain.

- **REG-008** Given an authenticated browser without a CSRF token, when it mutates configuration,
  then the request is rejected without mutation; error and access logs reveal no credentials.

- **REG-009** Given keyboard-only navigation and narrow or zoomed layouts, when the user completes
  registration, editing and deletion, then all controls and associated errors remain operable and
  understandable; focused interaction tests cover failures and successful submission.

- **REG-010** Given dev registration data, when down and then up complete, then it is absent; the
  registration screen explains this and startup does not seed or replace a mock user.

- **REG-011** Given a signed-in user denying Gmail consent, when the callback completes, then the
  session and existing configuration survive and no incomplete grant is saved.

- **REG-012** Given replayed, expired, cross-session or stale-version callback state, or a different
  Google subject, when the callback arrives, then it fails without changing ownership or tokens.

- **REG-013** Given an existing Gmail mailbox requiring reconnection, when Google sign-in
  completes, then Gmail authorization starts automatically without a separate reconnection screen.
  Given valid Gmail authorization, sign-in does not request mailbox consent again. When
  reconnection succeeds or omits a refresh token,
  then its positions survive and a valid existing same-account token is never overwritten by null;
  a new connection without a refresh token remains incomplete.

- **REG-014** Given a connected Gmail mailbox, when disconnected, then local credentials are removed
  and revocation is attempted; provider failure is reported without restoring deleted credentials.
  Signing out alone leaves scheduled scans authorized.

- **REG-015** Given Firestore or JPA/H2 selected for both applications against one database,
  when a user registers, edits, reconnects or deletes a mailbox, then the next producer scan sees
  the committed result with equivalent ownership/conflict and progress guarantees in both modes;
  missing or unknown DAO mode fails startup naming invoice-watcher.user-configuration.dao.mode.

## Configuration

Properties and startup requirements are listed in the generated reference:

- [Registration](../../reference/configuration.md#invoice-watcher-user-registration)
- [Google OAuth](../../reference/configuration.md#invoice-watcher-google-oauth)
- [Shared DAO](../../reference/configuration.md#invoice-watcher-user-configuration-dao)

## Out of scope

- Password authentication through registration, connecting another Google identity, Microsoft OAuth.
- Historical rescanning, account claiming/migration, key rotation and recall of published events.
