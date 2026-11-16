---
id: ADR-0018
status: accepted
date: 2026-11-12
---

# ADR-0018: Deploy Angular registration separately and share UI tokens and components

## Context and decision

Build the Spring backend and Angular frontend as separate applications and images. One gateway
origin routes assets to the frontend and API/OIDC callbacks to the backend. Sessions are backend
owned, HttpOnly, and CSRF protected. A single initial backend replica uses memory sessions;
restarts require sign-in without deleting stored registrations or grants.

Use Angular, pnpm workspaces and an internal shared Angular UI package under frontend/libs/ui.
Follow the [frontend tooling policy](../guidelines/frontend.md#current-tooling) when selecting
compatible releases; routine version upgrades do not change this architectural decision. Gradle owns
Node/pnpm provisioning, frozen-lockfile package installation and browser test images, using the pins
in frontend metadata. One :frontend Gradle workspace owns installation and shared tooling;
application wrappers consume its managed tools and build their configured Angular targets.
CI invokes Gradle rather than installing frontend tools independently.
Browser tests and their servers run inside the same pinned Playwright container locally and in CI.
Use its preinstalled browsers/system libraries and the workspace's exact Node/pnpm versions;
ordinary builds do not modify host OS packages. Docker is a build prerequisite. Run tests as the
host user and publish failure artifacts; baseline updates are explicit and container-based.
Backend tests provision Datastore through Testcontainers. Registration
consumes public library exports, and named CSS tokens own visual decisions. Shared components use
Shadow DOM, documented host/content contracts, accessible form associations and visible focus.
Ship a native Angular catalogue and Playwright interaction, axe and visual checks immediately.
Storybook was considered; a small catalogue avoids a second plugin/builder compatibility layer for
the initial controls while using the same compilation and browser fixtures. Revisit it if catalogue
needs grow. Review deterministic desktop/mobile screenshot changes with component/token changes.

The first frontend is an explicit amendment to ADR-0013's original staging threshold. Backend jars
never include frontend assets; the static frontend contains no OAuth or database credentials.
