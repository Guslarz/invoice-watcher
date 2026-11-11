# Frontend design system

This guideline defines how future frontend applications in this repository stay visually
consistent. No frontend application, shared frontend package or automated visual check exists yet;
this document records the current direction and the decisions intentionally deferred.

## Scope and current state

- **Applies to:** future user-facing frontend applications and any later shared frontend assets.
- **Current state:** research and direction only. Do not add frontend code, component packages,
  generated token packages or consistency automation as part of routine changes until a plan for the
  first frontend requires them.
- **Related work:** user registration is tracked separately in
  `docs/agents/inbox/user-registration-app.md` and must not be treated as implemented by this
  guideline.

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

- **Now:** design direction lives in `docs/agents/adr/` decisions and `docs/agents/guidelines/`
  guidance. This avoids inventing frontend module structure before a frontend exists.
- **When the first frontend is planned:** that plan must choose concrete repository locations for
  token definitions and any consumable artifacts (for example a dedicated frontend area or shared
  package). The plan must justify the location against existing repository conventions and name the
  consumers.
- **Do not assume:** do not place frontend assets under `libs/` or `apps/` by analogy to Java
  modules without a plan that defines build, ownership and release boundaries for frontend code.

## Consumption by future applications

- **First application:** consume the canonical tokens and patterns directly, keeping implementation
  choices local to that application unless reuse is clearly needed.
- **Multiple applications:** introduce a shared token artifact or package only after at least two
  consumers need the same implementation. The shared boundary must expose stable token names and
  documented usage, not application-specific components by default.
- **Framework choice:** deferred until the first frontend plan. Token names and accessibility
  requirements must remain usable regardless of the selected framework or styling approach.

## Shared component library requirements

Apply these requirements when a shared component library is introduced; no library or tooling is
implemented by this guideline.

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

## Consistency checks and staged adoption

Consistency tooling is intentionally not implemented now. The shared-library requirements above
become mandatory when that library is introduced; tool selection remains deferred.

1. **Stage 1 — documented contract (now):** maintain tokens, patterns and accessibility
   requirements in repository docs; use pull-request review for consistency.
2. **Stage 2 — first frontend:** add lightweight checks appropriate to the chosen stack, such as
   token/value linting, accessibility linting and focused interaction tests for critical flows.
3. **Stage 3 — shared library:** provide a component catalogue, Shadow DOM encapsulation and visual
   regression checks as described above. Evaluate shared packaging and additional design review
   automation when repeated implementation or drift justifies the maintenance cost.

When proposing checks later, prefer targeted, maintainable signals over broad screenshot suites that
are expensive to review.

## Decisions deferred until the first frontend exists

- Frontend framework, rendering model and styling technology.
- Directory layout and packaging for frontend code and shared assets.
- Token file format and generation pipeline, if any.
- Component library scope, versioning and release process.
- Automated lint, accessibility, interaction and visual regression tooling.
- Integration with external design tools as an authoring or export source.
