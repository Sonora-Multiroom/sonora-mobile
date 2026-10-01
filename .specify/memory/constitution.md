# sonora-mobile Constitution

sonora-mobile is the Android (later iOS) remote control for the Multiroom Audio Hub, built with
Kotlin Multiplatform and Compose Multiplatform. It talks only to the hub's REST API v2 on the home
LAN.

## Core Principles

### I. API Contract Fidelity (NON-NEGOTIABLE)

- `api/openapi.json` is the single source of truth for every hub interaction. The app MUST use
  only `/api/v2/**`; v1 paths (`/api/*` without `v2`) and extension paths (`/api/tts/*`) in the
  spec are out of bounds.
- The Kotlin client MUST be generated from the spec at build time. Generated code MUST NOT be
  hand-edited or committed.
- Screens and view-state logic MUST NOT use generated types directly; all hub access goes through
  one hand-written repository layer that maps generated types to app domain types.
- No guessed endpoints or fields. When the API lacks something, the feature spec MUST record it as
  a hub gap (and CLAUDE.md "Hub gaps" MUST list it), and the app works around it visibly. A gap
  MUST NOT be filled with invented behaviour or data.
- Refreshing `openapi.json` is a local task (cloud sessions cannot reach the hub). When the spec's
  API version changes, every affected call and its tests MUST be reconciled before further work
  touches those calls.
- This repository MUST NOT change the hub; backend changes belong to `multiroom-ai`.

Rationale: the hub is the only authority on audio state. A client that drifts from the contract
fails silently on a device the developer cannot debug from the cloud.

### II. Truthful UI

- The UI MUST show only what the hub actually knows. No progress bars, playback position, track
  metadata or artwork (the hub provides none). The only sliders are volume.
- Pause/Resume MUST appear only when `RouteResponse.pauseable` is true; "Move to room…" only when
  `transferable` is true. Non-pauseable (live) routes show Stop plus "Live streams can't be paused".
- States the hub distinguishes MUST be visually distinct: disabled (`enabled = false`, shown as
  "Off") vs unavailable (`available = false`, hardware not connected) vs idle vs playing vs paused
  vs failed (route `status = FAILED`).
- Every user action's outcome MUST be confirmed by hub state. While the user is interacting with
  a control, its local value wins; after the interaction ends and the request completes, the next
  refresh reconciles it. Volume changes are sent throttled, not on every drag event.
- Values the hub does not provide but the UI needs (e.g. input kind inferred from `uri`) MUST be
  derived in exactly one documented function, never re-derived ad hoc in screens.

Rationale: a remote control that lies about what is playing is worse than none; users act on what
they see.

### III. Shared-First Multiplatform

- Domain logic, state, networking and UI MUST live in `commonMain`.
- Platform source sets (`androidMain`, `iosMain`) MUST contain only what the platform requires:
  entry points, manifest, network security config, storage and engine drivers.
- Android is the shipping target. The iOS target MUST stay in the build configuration, and nothing
  Android-only (AndroidX, `android.*`, JVM-only APIs) may appear in `commonMain`, so that enabling
  iOS later is a CI job, not a refactor.

Rationale: the cost of keeping code shared is low today and prohibitive once Android-only
assumptions spread.

### IV. Test-First for Logic (NON-NEGOTIABLE)

- Logic that turns hub data into UI state MUST be written test-first in `commonTest`: joining
  outputs, groups, routes and inputs; group membership and "what plays on output X"; input-kind
  inference; enabled/available/status mapping; error mapping. Tests are written and seen failing
  before the implementation.
- The repository layer MUST be tested against a fake HTTP engine (Ktor `MockEngine`) with
  responses shaped by `api/openapi.json`, including RFC 7807 error bodies and network failures.
- Compose UI tests are optional until a feature spec asks for them.
- A change is done only when `./gradlew :composeApp:assembleDebug` succeeds and all tests pass
  (`:composeApp:testDebugUnitTest`, `:composeApp:allTests`).

Rationale: there is no emulator or hub in the cloud; unit tests are the only verification an
implementing session has before the user installs the APK.

### V. Resilient LAN Networking

- The hub may be offline, restarting or unreachable. The app MUST NOT crash or hang on that; it
  MUST show a clear connection state and recover on its own when the hub returns.
- Requests MUST use short timeouts. Polling (~2.5 s) runs only while the app is in the foreground
  and stops in the background (the hub has no push channel).
- The client MUST tolerate a newer hub: ignore unknown JSON fields, map unknown enum values to an
  explicit Unknown, and never fail a whole response because of one unrecognised value.
- RFC 7807 problem details MUST be shown to the user in plain words, never as raw messages, codes
  or stack traces.
- Plain HTTP without authentication on the LAN is intentional. The hub address is a user setting
  with no default; cleartext is allowed app-wide because the address is user-entered.

Rationale: a home LAN hub restarts, sleeps and changes address; the app must treat that as normal.

### VI. Design Fidelity & Accessibility

- The Claude Design canvas linked in CLAUDE.md (offline copies in `design/screens/`) is the visual
  source of truth. Colours and typography MUST come from theme tokens; spacing and radii MUST use
  tokens where one exists.
- A UI change that departs from the design MUST either update the design first or be called out
  explicitly in the feature spec.
- Agreed details (volume pill that never resembles a progress bar, "Move to room…" label, "will
  stop" notice when starting on a busy target) MUST be kept.
- Touch targets MUST be ≥ 44 dp, text contrast ≥ 4.5:1 against its background, and every
  icon-only control MUST have a content description.

Rationale: the design was agreed deliberately; drift and inaccessible controls are both defects.

### VII. Minimal, Justified Dependencies

- Prefer Kotlin, JetBrains and AndroidX libraries (AndroidX only in platform source sets unless
  multiplatform). Every new dependency MUST be justified in the feature plan.
- All versions MUST live in `gradle/libs.versions.toml`, using current stable releases looked up
  at the time, not recalled from memory.

Rationale: each dependency is a multiplatform compatibility risk and a future iOS blocker.

## Platform & Technology Constraints

- Stack: Kotlin Multiplatform + Compose Multiplatform, Ktor client, kotlinx.serialization, client
  generated by OpenAPI Generator (`kotlin`, `library=multiplatform`) or an equivalent build-time
  generator.
- Package / applicationId: `ai.sonora.mobile`, changed only at the user's request.
- Project layout follows the JetBrains KMP wizard layout described in CLAUDE.md.
- Fonts (Sora, DM Sans) are bundled under `composeResources/font`; their OFL licences live in
  `docs/licenses/`.
- Architectural decisions recorded in CLAUDE.md ("Decisions") MUST NOT be revisited without asking
  the user.

## Development Workflow & Quality Gates

- Specs are written locally, where the hub and `multiroom-ai` are reachable, with Spec Kit
  (specify → clarify → plan → tasks) on a feature branch `NNN-short-name` with sequential
  numbering, then pushed.
- Cloud sessions implement with `/speckit-implement` on that same branch and keep commits there.
  When a task needs a decision the spec does not cover, the session MUST stop and ask rather than
  invent behaviour.
- Every plan MUST pass the Constitution Check against these principles; any deviation is recorded
  in the plan's complexity tracking with its justification.
- There is no CI for Android. The merge gate is a local or cloud build plus all tests green. A
  GitHub Actions macOS runner will exist only for iOS builds.
- Commits use Conventional Commit messages that explain why, not just what.

## Governance

- This constitution overrides other project practice. Where CLAUDE.md, a spec or a plan conflicts
  with it, the constitution wins until amended.
- Amendments go through `/speckit-constitution`, carry a version bump and MUST be accompanied by a
  sync of CLAUDE.md in the same change.
- Versioning follows semver: MAJOR for removed or redefined principles, MINOR for added principles
  or materially expanded guidance, PATCH for wording and clarifications.
- Reviews of specs, plans and implementations MUST check compliance with every principle;
  unjustified complexity or deviation is a blocking finding.

**Version**: 1.1.0 | **Ratified**: 2026-10-01 | **Last Amended**: 2026-10-01
