# Registration frontend and shared UI

Gradle provisions the pinned Node LTS version from `.node-version`, pnpm from `package.json`,
workspace dependencies from the lockfile and the pinned Docker environment for browser tests. No global Node, npm or
pnpm installation is required. Angular uses the supported Angular 22 release line with Node 26 LTS. Runtime, framework and
compatible tooling versions are pinned exactly; `engines.node` matches `.node-version`.

Run these commands from the repository root:

```shell
./gradlew :apps:user-registration-frontend-app:frontendBuild
./gradlew :apps:user-registration-frontend-app:check
```

Full builds and browser checks run Playwright and its Angular test servers in Docker. The image
matches the exact Playwright package version and includes the browser and its system libraries.
Gradle builds the test image using the exact workspace Node/pnpm pins, installs dependencies
inside it using the frozen lockfile, and runs it as the host user. No host OS packages or browser
binaries are installed; an accessible Docker daemon is required. App compilation and development
servers continue to use Gradle's managed host toolchain.

Browser tests publish no host ports and can run alongside development servers. Failure artifacts
are written to the app Gradle project's build/playwright/test-results directory. Baselines are
mounted read-only during checks; only frontendUpdateSnapshots mounts them writable. Generate and
review baselines in this same container environment locally and in CI.

`apps/user-registration` is the Angular application. `libs/ui` is the shared Angular package, built
before consumers and imported through `@invoice-watcher/ui`. Its CSS custom properties under
`libs/ui/tokens` own colors, typography, spacing, borders and focus treatment. Application screens
use these tokens and public components; business-specific Gmail flows stay in the application.
The package is internal to this pnpm workspace and is not published to an external registry.

Use `./gradlew :apps:user-registration-frontend-app:frontendServe` for registration with its
backend on loopback port 8080. Use
`./gradlew :frontend:frontendCatalogueServe` for the component catalogue
on port 4201. Both tasks use the managed toolchain. The catalogue is a dedicated Angular build
target, not a deployed application. Storybook was evaluated; a small native Angular catalogue keeps
the initial controls executable with the same compiler and Playwright fixtures without another
builder/plugin compatibility boundary.

Shared components use Shadow DOM. Public inputs and token names form their styling contract;
application styles cannot select internal controls. Host layout and inherited design tokens are
intentional inputs; arbitrary CSS parts or internal style overrides are not supported. Projected
content is limited to action/notice text. Input components own their label, hints, error and native
input within one shadow root to keep accessible associations intact.

Playwright tests exercise application flows, axe accessibility checks, focus, style encapsulation
and deterministic desktop/mobile catalogue screenshots. Baselines live under `tests/baselines`;
CI compares them without updating. Use `./gradlew :apps:user-registration-frontend-app:frontendUpdateSnapshots` only for an
intentional visual change and review the image diff in the same pull request. Keep browser version,
fonts, locale, time zone and reduced-motion settings stable. Token renames or meaning changes
require a consumer migration note. Generated `dist` packages and reports are ignored.

The `:frontend` Gradle project owns the single workspace installation, Node/pnpm provisioning,
shared UI/catalogue builds and the Playwright test image. App projects under `apps/` apply
`frontend.app-conventions` and set `frontendApplication.angularProject` and `outputPath`;
`playwrightConfig` selects their browser suite. Apps consume managed tool artifacts from the
workspace, so parallel app builds share one installation task. Keep Angular output paths distinct
and use separate Playwright configurations/ports when adding concurrently tested applications.

Components use adjacent TypeScript, HTML and SCSS files. Bootstrap entry points only start the
application; typed API access, account orchestration, folder editing and validation have separate
files. Component-scoped signal stores own account and folder state and commands; components
project read-only state and forward UI events. Shared public-api.ts only exports the library's supported components. Format source with
`./gradlew :frontend:frontendFormat`; full checks enforce formatting automatically. Page layout
styles are shared through the UI SCSS partial, and focus outlines use the focus-offset token.

Browser verification uses container-internal ports 4300 and 4301, independent of local development on 4200.

`.node-version` is the common version-manager format used by nodenv and other tools; Gradle
reads it to provision the exact runtime. Keep it aligned with package.json engines.node.
Each Angular target owns its entry HTML and global stylesheet beside its sources; shared document
and page styling lives in libs/ui/styles. The workspace root holds tooling and workspace metadata.
Prettier uses Google's gts formatting settings. Broader Angular/Google TypeScript style enforcement
is tracked separately in the agent inbox.
