# Frontend design system

This guideline governs the Angular registration frontend and shared UI package. The frontend
workspace owns build/release tooling; the Java applications remain separate runtime deployments.

## Scope and current state

- Registration consumes the shared Angular components and versioned design tokens under
  frontend/libs/ui. See the frontend README for build, catalogue and browser-check commands.
- This first consumer introduces the shared package under the amended ADR-0013. Keep mailbox
  workflows in the application and reusable controls in the UI package.

## Source organization and formatting

Follow the [Angular style guide](https://angular.dev/style-guide) and
[Google TypeScript style guide](https://google.github.io/styleguide/tsguide.html), subject to the
explicit repository conventions below. Formatting uses the settings published by
[Google gts](https://github.com/google/gts/blob/main/.prettierrc.json); formatting alone does not
establish full style-guide compliance. Broader linting/alignment is tracked in
[the inbox](../inbox/enforce-angular-google-typescript-style.md).

- Put each Angular target's entry HTML and global stylesheet alongside that target's sources.
  Keep genuinely shared design styles in the UI library; the workspace root owns tooling only.

- Use signals for reactive component state, signal inputs and model bindings, and computed signals
  for derived state. Use output() for component events. Update signal-held objects/arrays
  immutably. Set ChangeDetectionStrategy.OnPush explicitly on every component, including
  catalogue/demo components. Keep stateless components stateless.

- Keep bootstrap entry points focused on application startup. Put each component in its own
  TypeScript file with adjacent HTML and SCSS files referenced by templateUrl and styleUrl.
- Keep components as projections of state: render read-only signals and forward UI events to
  commands. Put workflow state, async operations, validation and payload transformations in
  focused injectable services or signal stores. Scope mutable feature stores to the owning
  component's providers so state is not shared across unrelated instances. Expose read-only
  signals and explicit commands; templates must not mutate store signals directly. Native
  control event adaptation stays in reusable controls. Do not add a store dependency just to
  wrap stateless markup or framework lifecycle wiring.
- Keep HTTP/CSRF handling in typed services, transport models in model files, and folder editing
  and validation separate from account workflow orchestration. Shared public-api files only export
  supported library interfaces and components.
- Declare explicit return types on TypeScript methods and named functions, including void and
  Promise<void>. Constructors have no return annotation; callbacks may use contextual typing.
- Separate the import block from declarations or executable code with a blank line in TypeScript.
- Format frontend source and configuration with the pinned Prettier version. Gradle's
  :frontend:frontendFormat applies formatting; frontend verification runs frontendFormatCheck.
- Focus outlines use shared width and offset tokens: 1px thick with a 2px gap from the control.

## Design sources

- **Canonical source:** repository-owned, versioned design tokens and documented UI patterns.
  Tokens name foundational decisions such as color roles, typography scales, spacing, layout,
  elevation, motion, iconography and focus treatment.
- **Documentation:** this guideline and future frontend-specific docs describe how tokens and
  patterns are used. Documentation explains intent and constraints; tokens provide the shared names
  and values.
- **Design tools:** sketches or design-tool files may inform decisions, but they are not the source
  of truth until the repository defines how their outputs are exported into reviewable token files.
  A contributor must be able to understand the current design direction from the repository alone.

## Where design sources live

Canonical CSS tokens and public Angular components live under frontend/libs/ui. The package's
catalogue and README document supported states and usage. Application workflows live under
frontend/apps/user-registration. The frontend workspace is registered as the Gradle project :frontend;
apps/user-registration-frontend-app bridges its build and verification into Gradle, while image
modules package the backend and frontend independently.

## Consumption by future applications

- **First application:** use the shared UI package and canonical tokens; keep business-specific
  screens and consent workflows local to registration.
- **Shared package:** registration introduces the initial token/component boundary; additional
  applications consume its public exports. Expand shared implementation when consumers need it.
  The shared boundary must expose stable token names and
  documented usage, not application-specific components by default.
- **Framework choice:** Angular with pnpm. Token names and accessibility
  requirements must remain usable regardless of the selected framework or styling approach.

## Shared component library requirements

Apply these requirements to the shared component library and any future additions.

- **Component catalogue:** provide a browsable catalogue of supported component states and usage,
  including variants, loading, empty, error, disabled and focus states where applicable. Evaluate
  Storybook as the default; select it or an alternative with the frontend stack based on framework
  support, maintenance, interaction testing and CI integration. For tokens alone, a lightweight
  visual reference is sufficient.
- **Style encapsulation:** use Shadow DOM for shared components so application styles cannot select
  and override their internal elements. Keep component styling owned by the design system; consumers
  select supported variants rather than supplying internal CSS overrides. Shadow DOM is not total
  style isolation: inherited properties, CSS custom properties, host styling and slotted content
  cross different boundaries. Explicitly establish internal style defaults, constrain host and slot
  usage through the documented component contract, and expose only design-system-owned token and
  theme inputs. Do not expose internal styling hooks such as CSS parts for arbitrary consumer
  overrides. Verify encapsulation alongside keyboard, focus and accessible-name behavior.
- **Visual regression:** include automated screenshot comparison against reviewed baselines when the
  library is introduced, not only after multiple applications adopt it. Use catalogue examples as
  fixtures covering supported component states, themes and representative viewport sizes. Make
  rendering deterministic (including fonts, data and animations), run comparisons in CI, and require
  explicit review of detected differences before updating baselines. Choose the tool and comparison
  tolerances with the stack; visual checks complement interaction and accessibility tests rather than
  replace them.

## Governance

- **Changes:** propose token or guidance changes through a pull request. Describe the user-facing
  reason, affected patterns, accessibility impact and any migration needed by existing or planned
  consumers.
- **Review:** reviewers check that names are consistent, values have clear intent, and changes do
  not silently weaken accessibility or create application-specific exceptions without justification.
- **Compatibility:** treat token renames and semantic changes as breaking for consumers. Record the
  replacement and migration steps in the same change.
- **Ownership:** until a frontend team or application exists, design-system changes are owned by the
  change proposing them and reviewed like other cross-cutting repository decisions.

## Accessibility requirements

Future frontends must meet these baseline expectations; plans may strengthen them but must not relax
them without an accepted decision:

- **Color and contrast:** define color tokens by role and ensure text and essential UI states meet
  WCAG 2.2 AA contrast requirements.
- **Typography and spacing:** use readable type scales and spacing that support reflow and zoom
  without loss of content or function.
- **Focus and keyboard:** every interactive element must have a visible focus state and be operable
  with a keyboard.
- **Semantics:** prefer native semantics and accessible names; patterns that rely on color, motion
  or position alone must provide an equivalent non-visual cue.
- **Motion:** respect reduced-motion preferences for non-essential animation.
- **Forms and errors:** labels, instructions and error messages must be programmatically associated
  and understandable without relying on placeholder text alone.

## Consistency checks

The shared library ships with its first consumer. Token usage lint, catalogue fixtures,
interaction/accessibility checks and visual baseline comparison run in verification and CI.
Keep these checks focused on supported states and representative viewports; expand fixtures when
new component contracts are introduced. Review visual differences explicitly before updating
baselines. Token renames and semantic changes require a documented consumer migration.

## Current tooling

Use a supported stable Angular release compatible with the selected Node LTS release. Angular
may be in active support or LTS. Pin exact compatible tooling versions in frontend metadata; keep
the Node version in package.json engines aligned with .node-version. Use pnpm workspaces and
frozen-lockfile CI installs. The native Angular component
catalogue and Playwright browser fixtures provide component states, interaction/accessibility
checks and desktop/mobile visual baselines. Review baseline changes explicitly in pull requests.
External design tools remain optional authoring inputs; repository tokens are canonical.
